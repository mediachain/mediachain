package io.mediachain.copycat

import java.util.function.Consumer
import java.util.{Random, Timer, TimerTask}
import java.util.concurrent.{ExecutorService, Executors, CompletableFuture}
import java.time.Duration
import io.atomix.copycat.Operation
import io.atomix.copycat.client.{CopycatClient, ConnectionStrategy}
import io.atomix.catalyst.transport.Address
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Future, Promise}
import scala.util.{Try, Success, Failure}
import scala.compat.java8.FutureConverters
import scala.collection.JavaConversions._

import cats.data.Xor

import io.mediachain.copycat.StateMachine._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor._
import io.mediachain.util.Logging

class Client(sslConfig: Option[Transport.SSLConfig]) extends JournalClient {
  import scala.concurrent.ExecutionContext.Implicits.global
  import Client._
  import ClientState._
  
  @volatile private var shutdown = false
  @volatile private var state: ClientState = Disconnected
  @volatile private var client = newCopycatClient()
  private var cluster: Option[List[Address]] = None
  private var exec: ExecutorService = Executors.newSingleThreadExecutor()
  private var recovery: Option[CompletableFuture[CopycatClient]] = None
  private var listeners: Set[JournalListener] = Set()
  private var stateListeners: Set[ClientStateListener] = Set()
  private val logger = LoggerFactory.getLogger(classOf[Client])
  private val timer = new Timer(true) // runAsDaemon
  private val maxRetries = 5
  private val withErrorLog = Logging.withErrorLog(logger) _
  
  // submit a state machine operation with retry logic to account
  // for potentially transient client connectivity errors
  private def submit[T](op: Operation[T], retry: Int = 0): Future[T] = {
    (state, shutdown) match {
      case (_, true) =>
        Future.failed {new ClientException("Copycat client has shutdown")}

      case (Disconnected, _) => 
        Future.failed {new ClientException("Copycat client is disconnected")}

      case (Suspended, _) =>
        if (retry < maxRetries) {
          logger.info("Copycat client is suspended; delaying "+ op)
          backoff(retry).flatMap { retry =>
            logger.info("Retrying operation " + op)
            submit(op, retry + 1)
          }
        } else {
          logger.warn("Copycat client is unavailable; aborting " + op)
          Future.failed {new ClientException("Copycat client unavailable; giving up")}
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
    val delay = Client.randomBackoff(retry)
    
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
        emitStateChange(Connected)
        
      case CopycatClient.State.SUSPENDED =>
        state = Suspended
        if (!shutdown) {
          logger.info("Copycat session suspended; attempting to recover")
          emitStateChange(Suspended)
          recover()
        }
        
      case CopycatClient.State.CLOSED =>
        if (!shutdown) {
          val ostate = state
          state = Suspended
          logger.info("Copycat session closed; attempting to reconnect")
          if (ostate != state) {
            emitStateChange(Suspended)
          }
          reconnect()
        } else {
          disconnect("Copycat session closed")
        }
    }
  }
  
  private def emitStateChange(stateChange: ClientState) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(stateListeners.foreach(_.onStateChange(stateChange)))
      }})
  }
  
  private def disconnect(what: String) {
    state = Disconnected
    logger.info(what)
    emitStateChange(Disconnected)
  }
  
  private def recover() {
    val cf = client.recover()
    recovery = Some(cf)
    exec.submit(new Runnable {
      def run {
        try {
          cf.join()
          logger.info("Copycat session recovered")
        } catch {
          case e: Throwable =>
            logger.error("Copycat session recovery failed", e)
        } finally {
          recovery = None
        }
      }})
  }
  
  private def reconnect() {
    def loop(retry: Int) {
      if (!shutdown) {
        if (retry < maxRetries) {
          logger.info("Reconnecting to cluster")
          // Copycat client state is already closed if we are reconnecting, 
          // but also close it to shutdown its internal context
          val klient = client
          client = newCopycatClient()
          klient.close()
          Try(doConnect()) match {
            case Success(_) => 
              logger.info(s"Successfully reconnected to cluster")
              if (shutdown) {
                // lost race with user calling #close
                // make sure the client is closed
                client.close()
              }
            case Failure(e) =>
              logger.error("Connection error", e)
              val sleep = Client.randomBackoff(retry)
              logger.info("Backing off reconnect for " + sleep + " ms")
              Thread.sleep(sleep)
              loop(retry + 1) 
          }
        } else {
          disconnect("Failed to reconnect; giving up.")
        }
      } else {
        disconnect("Client has shutdown")
      }
    }
    
    recovery.foreach(_.cancel(false))
    exec.submit(new Runnable {
      def run { 
        try { 
          loop(0)
        } catch {
          case e: InterruptedException => 
            disconnect("Client reconnect interrupted")
          case e: Throwable =>
            logger.error("Unhandled exception in Client#reconnect", e)
            disconnect("Client reconnect failed")
        }
      }})
  }
  
  private def newCopycatClient(): CopycatClient = {
    val klient = Client.buildCopycatClient(sslConfig)
    klient.onStateChange(new Consumer[CopycatClient.State] {
      def accept(state: CopycatClient.State) {
        if (klient eq client) {
          onStateChange(state)
        } 
      }})
    klient
  }
  
  def addStateListener(listener: ClientStateListener) {
    stateListeners += listener
  }

  // Journal 
  def insert(rec: CanonicalRecord): Future[Xor[JournalError, CanonicalEntry]] =
    submit(JournalInsert(rec))

  def update(ref: Reference, cell: ChainCell): Future[Xor[JournalError, ChainEntry]] =
    submit(JournalUpdate(ref, cell))
  
  def lookup(ref: Reference): Future[Xor[JournalError, Option[Reference]]] = 
    submit(JournalLookup(ref))
  
  def currentBlock: Future[JournalBlock] =
    submit(JournalCurrentBlock())
  
  // JournalClient
  def connect(addresses: List[String]) {
    if (!shutdown) {
      val clusterAddresses = addresses.map {a => new Address(a)}
      cluster = Some(clusterAddresses)
      doConnect()
    } else {
      throw new IllegalStateException("client has been shutdown")
    }
  }
  
  private def doConnect() {
    val klient = client
    cluster.foreach { addrs => klient.connect(addrs).join() }
    klient.onEvent("journal-commit", new Consumer[JournalCommitEvent] { 
      def accept(evt: JournalCommitEvent) {
        if (klient eq client) {
          onJournalCommitEvent(evt)
        }
      }})
    
    klient.onEvent("journal-block", new Consumer[JournalBlockEvent] { 
      def accept(evt: JournalBlockEvent) { 
        if (klient eq client) {
          onJournalBlockEvent(evt)
        }
      }})
  }
  
  private def onJournalCommitEvent(evt: JournalCommitEvent) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(listeners.foreach(_.onJournalCommit(evt.entry)))
      }})
  }
  
  private def onJournalBlockEvent(evt: JournalBlockEvent) {
    exec.submit(new Runnable {
      def run {
        withErrorLog(listeners.foreach(_.onJournalBlock(evt.ref, evt.index)))
      }})
  }

  def close() {
    if (!shutdown) {
      shutdown = true
      exec.shutdownNow()
      client.close.join()
    }
  }
  
  def listen(listener: JournalListener) {
    listeners += listener
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
  
  class ClientConnectionStrategy extends ConnectionStrategy {
    val maxRetries = 10
    val logger = LoggerFactory.getLogger(classOf[ClientConnectionStrategy])
    
    def attemptFailed(at: ConnectionStrategy.Attempt) {
      val retry = at.attempt - 1
      if (retry < maxRetries) {
        val sleep = randomBackoff(retry)
        logger.info(s"Connection attempt ${at.attempt} failed. Retrying in ${sleep} ms")
        at.retry(Duration.ofMillis(sleep))
      } else {
        logger.error(s"Connection attempt ${at.attempt} failed; giving up.")
        at.fail()
      }
    }
  }

  val random = new Random  
  def randomBackoff(retry: Int, max: Int = 60) = 
    random.nextInt(Math.min(max, Math.pow(2, retry).toInt) * 1000)

  def buildCopycatClient(sslConfig: Option[Transport.SSLConfig]): CopycatClient = {
    val client = CopycatClient.builder()
      .withTransport(Transport.build(2, sslConfig))
      .withConnectionStrategy(new ClientConnectionStrategy)
      .build()
    Serializers.register(client.serializer)
    client
  }
  
  def build(sslConfig: Option[Transport.SSLConfig] = None): Client = {
    new Client(sslConfig)
  }
}
