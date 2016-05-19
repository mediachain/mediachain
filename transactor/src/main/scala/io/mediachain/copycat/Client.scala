package io.mediachain.copycat

import java.util.function.Consumer
import java.util.{Random, Timer, TimerTask}
import java.util.concurrent.{ExecutorService, Executors}
import io.atomix.copycat.Operation
import io.atomix.copycat.client.{CopycatClient, ConnectionStrategies}
import io.atomix.catalyst.transport.Address
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Future, Promise}
import scala.util.{Try, Success, Failure}
import scala.compat.java8.FutureConverters

import cats.data.Xor

import io.mediachain.copycat.StateMachine._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor._

class Client(client: CopycatClient) extends JournalClient {
  import scala.concurrent.ExecutionContext.Implicits.global
  import Client._
  import ClientState._
  
  @volatile private var shutdown = false
  @volatile private var state: ClientState = Disconnected
  private var server: Option[String] = None
  private var recoveryExecutor: ExecutorService = Executors.newSingleThreadExecutor()
  private var listeners: Set[JournalListener] = Set()
  private var stateListeners: Set[ClientStateListener] = Set()
  private val logger = LoggerFactory.getLogger(classOf[Client])
  private val random = new Random
  private val timer = new Timer(true) // runAsDaemon
  private val maxRetries = 5
  
  client.onStateChange(new Consumer[CopycatClient.State] {
    def accept(state: CopycatClient.State) {
      onStateChange(state)
    }})
  
  // submit a state machine operation with retry logic to account
  // for potentially transient client connectivity errors
  private def submit[T](op: Operation[T], retry: Int = 0): Future[T] = {
    (state, shutdown) match {
      case (_, true) =>
        Future.failed {new ClientException("Copycat client has shutdown")}

      case (Disconnected, _) => 
        Future.failed {new ClientException("Copycat client is disconnected")}

      case (Suspended, _) =>
        logger.info("Copycat client is suspended; delaying "+ op)
        backoff(1).flatMap { _ =>
          logger.info("Retrying operation " + op)
          submit(op, retry)
        }
        
      case (Connected, _) =>
        val fut = FutureConverters.toScala(client.submit(op))
        if (retry < maxRetries) {
          fut.recoverWith { 
            case e: Throwable =>
              logger.error("Copycat client error in " + op, e)
              backoff(retry).flatMap { retry => 
                logger.info("Retrying operation " + op)
                submit(op, retry + 1)
              }
          }
        } else {
          fut
        }
    }
  }
  
  private def backoff(retry: Int): Future[Int] = {
    val promise = Promise[Int]
    val delay = random.nextInt(Math.pow(2, retry).toInt * 1000)
    
    logger.info("Backing off for " + delay + " ms")
    timer.schedule(new TimerTask {
      def run {
        promise.complete(Try(retry))
      }
    }, delay)
    promise.future
  }
  
  private def onStateChange(cstate: CopycatClient.State) {
    cstate match {
      case CopycatClient.State.CONNECTED => 
        state = Connected
        logger.info("Copycat session connected")
        stateListeners.foreach(_.onStateChange(Connected))
        
      case CopycatClient.State.SUSPENDED =>
        state = Suspended
        if (!shutdown) {
          logger.info("Copycat session suspended; attempting to recover")
          stateListeners.foreach(_.onStateChange(Suspended))
          recover()
        }
        
      case CopycatClient.State.CLOSED =>
        if (!shutdown) {
          val ostate = state
          state = Suspended
          logger.info("Copycat session closed; attempting to reconnect")
          if (ostate != state) {
            stateListeners.foreach(_.onStateChange(Suspended))
          }
          reconnect()
        } else {
          disconnect("Copycat session closed")
        }
    }
  }
  
  private def disconnect(what: String) {
    state = Disconnected
    logger.info(what)
    stateListeners.foreach(_.onStateChange(Disconnected))
  }
  
  private def recover() {
    val cf = client.recover()
    recoveryExecutor.submit(new Runnable {
      def run {
        try {
          cf.join()
        } catch {
          case e: InterruptedException => ()
          case e: Throwable =>
            logger.error("Session recovery failed", e)
        }
      }})
  }
  
  private def reconnect() {
    def loop(address: String, retry: Int) {
      if (!shutdown) {
        if (retry < maxRetries) {
          logger.info("Reconnecting to " + address)
          Try(client.connect(new Address(address)).join()) match {
            case Success(_) => 
              if (shutdown) {
                // lost race with user calling #close
                // make sure the client is closed
                client.close()
              }
            case Failure(e) =>
              logger.error("Connection error", e)
              val sleep = random.nextInt(Math.pow(2, retry).toInt * 1000)
              logger.info("Backing off reconnect for " + sleep + " ms")
              Thread.sleep(sleep)
              loop(address, retry + 1) 
          }
        } else {
          disconnect("Failed to reconnect; giving up.")
        }
      } else {
        disconnect("Client has shutdown")
      }
    }
    
    recoveryExecutor.submit(new Runnable {
      def run { 
        try {
          server.foreach { address => loop(address, 0) }
        } catch {
          case e: InterruptedException => 
            disconnect("Client reconnect interrupted")
          case e: Throwable =>
            logger.error("Unhandled exception in Client#reconnect", e)
            disconnect("Client reconnect failed")
        }
      }})
  }
  
  def addStateListener(listener: ClientStateListener) {
    stateListeners += listener
  }

  // Journal 
  def insert(rec: CanonicalRecord): Future[Xor[JournalError, CanonicalEntry]] =
    submit(JournalInsert(rec))

  def update(ref: Reference, cell: ChainCell): Future[Xor[JournalError, ChainEntry]] =
    submit(JournalUpdate(ref, cell))
  
  def lookup(ref: Reference): Future[Option[Reference]] = 
    submit(JournalLookup(ref))
  
  def currentBlock: Future[JournalBlock] =
    submit(JournalCurrentBlock())
  
  // JournalClient
  def connect(address: String) {
    if (!shutdown) {
      server = Some(address)
      client.connect(new Address(address)).join()
    } else {
      throw new IllegalStateException("client has been shutdown")
    }
  }
  
  def close() {
    if (!shutdown) {
      shutdown = true
      recoveryExecutor.shutdownNow()
      client.close().join()
    }
  }
  
  def listen(listener: JournalListener) {
    if (listeners.isEmpty) {
      listeners = Set(listener)
      client.onEvent("journal-commit", 
                     new Consumer[JournalCommitEvent] { 
        def accept(evt: JournalCommitEvent) {
          listeners.foreach(_.onJournalCommit(evt.entry))
        }
      })
      client.onEvent("journal-block", 
                     new Consumer[JournalBlockEvent] { 
        def accept(evt: JournalBlockEvent) { 
          listeners.foreach(_.onJournalBlock(evt.ref))
        }
      })
    } else {
      listeners += listener
    }
  }
}

object Client {

  class ClientException(what: String) extends RuntimeException(what)

  sealed abstract class ClientState
  
  object ClientState {
    case object Connected extends ClientState
    case object Suspended extends ClientState
    case object Disconnected extends ClientState
  }

  trait ClientStateListener {
    def onStateChange(state: ClientState): Unit
  }
  
  def build(sslConfig: Option[Transport.SSLConfig] = None): Client = {
    val client = CopycatClient.builder()
      .withTransport(Transport.build(2, sslConfig))
      .withConnectionStrategy(ConnectionStrategies.EXPONENTIAL_BACKOFF)
      .build()
    Serializers.register(client.serializer)
    new Client(client)
  }
}
