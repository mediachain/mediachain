package io.mediachain.transactor

import java.io.File
import java.util.function.{Consumer, Supplier}
import java.util.{Random, Timer, TimerTask}
import io.atomix.copycat.Operation
import io.atomix.copycat.client.{CopycatClient, ConnectionStrategies}
import io.atomix.copycat.server.{CopycatServer, StateMachine => CopycatStateMachine}
import io.atomix.copycat.server.storage.{Storage, StorageLevel}
import io.atomix.catalyst.transport.{Address, NettyTransport}
import io.atomix.catalyst.serializer.Serializer
import org.slf4j.{Logger, LoggerFactory}

import scala.concurrent.{Future, Promise}
import scala.util.Try
import scala.compat.java8.FutureConverters

import cats.data.Xor

object Copycat {
  import io.mediachain.transactor.StateMachine._
  import io.mediachain.protocol.Datastore._
  import io.mediachain.protocol.Transactor._


  object Server {
    def build(address: String, logdir: String, datastore: Datastore,
              blocksize: Int = StateMachine.JournalBlockSize): CopycatServer = {
      def stateMachineSupplier() = {
        new Supplier[CopycatStateMachine] {
          override def get: CopycatStateMachine = {
            new JournalStateMachine(datastore, blocksize)
          }
        }
      }
      
      val server = CopycatServer.builder(new Address(address))
                    .withStateMachine(stateMachineSupplier())
                    .withStorage(Storage.builder()
                                  .withDirectory(new File(logdir))
                                  .withStorageLevel(StorageLevel.DISK)
                                  .build())
                    .withTransport(NettyTransport.builder()
                                    .withThreads(4)
                                    .build())
                    .build()
      Serializers.register(server.serializer)
      server
    }
  }
  
  class Client(client: CopycatClient) extends JournalClient {
    import scala.concurrent.ExecutionContext.Implicits.global
    
    private var shutdown = false
    private var server: Option[String] = None
    private var listeners: Set[JournalListener] = Set()
    private val logger = LoggerFactory.getLogger(classOf[Client])
    private val random = new Random
    private val timer = new Timer(true) // runAsDaemon
 
    client.onStateChange(new Consumer[CopycatClient.State] {
                              def accept(state: CopycatClient.State) {
                                onStateChange(state)
                              }
    })
   
    def copycat = client
    
    // submit a state machine operation with retry logic to account
    // for potentially transient client connectivity errors
    val maxRetries = 5
    private def submit[T](op: Operation[T], retry: Int = 0): Future[T] = {
      val fut = FutureConverters.toScala(client.submit(op))
      if (retry < maxRetries) {
        fut.recoverWith { 
          case e: Throwable =>
            logger.error("Copycat client error in " + op, e)
            backoff(retry).flatMap { retry => 
              if (!shutdown) {
                logger.info("Retrying operation " + op)
                submit(op, retry + 1)
              } else {
                Future {throw new RuntimeException("client shutdown")}
              }
            }
        }
      } else {
        fut
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
    
    private def onStateChange(state: CopycatClient.State) {
      state match {
        case CopycatClient.State.CONNECTED => 
          logger.info("Copycat client connected")
          
        case CopycatClient.State.SUSPENDED =>
          if (!shutdown) {
            logger.info("Copycat session suspended; attempting to recover")
            client.recover()
          }
          
        case CopycatClient.State.CLOSED =>
          if (!shutdown) {
            server.foreach { address =>
              logger.info("Copycat session closed; attempting to reconnect")
              client.connect(new Address(address))
            }
          }
      }
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
      server = Some(address)
      client.connect(new Address(address)).join()
    }
    
    def close() {
      shutdown = true
      client.close().join()
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
    def build(): Client = {
      val client = CopycatClient.builder()
                    .withTransport(NettyTransport.builder()
                                    .withThreads(2)
                                    .build())
                    .withConnectionStrategy(ConnectionStrategies.EXPONENTIAL_BACKOFF)
                    .build()
      Serializers.register(client.serializer)
      new Client(client)
    }
    
  }

  object Serializers {
    val klasses = List(classOf[JournalInsert],
                       classOf[JournalUpdate],
                       classOf[JournalLookup],
                       classOf[JournalCurrentBlock],
                       classOf[JournalCommitEvent],
                       classOf[JournalBlockEvent],
                       classOf[JournalState])
    def register(serializer: Serializer) {
      klasses.foreach(serializer.register(_))
    }
  }
  
  
}
