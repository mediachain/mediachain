package io.mediachain.transactor

import java.io.File
import java.util.function.{Consumer, Supplier}
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.{CopycatServer, StateMachine => CopycatStateMachine}
import io.atomix.copycat.server.storage.{Storage, StorageLevel}
import io.atomix.catalyst.transport.{Address, NettyTransport}
import io.atomix.catalyst.serializer.Serializer

import scala.concurrent.Future
import scala.compat.java8.FutureConverters

import cats.data.Xor

object Copycat {
  import io.mediachain.transactor.StateMachine._
  import io.mediachain.transactor.Types._

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
    private var listeners: Set[JournalListener] = Set()
    
    // Journal 
    def insert(rec: CanonicalRecord): Future[Xor[JournalError, CanonicalEntry]] =
      FutureConverters.toScala(client.submit(JournalInsert(rec)))

    def update(ref: Reference, cell: ChainCell): Future[Xor[JournalError, ChainEntry]] =
      FutureConverters.toScala(client.submit(JournalUpdate(ref, cell)))
    
    def lookup(ref: Reference): Future[Option[Reference]] = 
      FutureConverters.toScala(client.submit(JournalLookup(ref)))
    
    def currentBlock: Future[JournalBlock] =
      FutureConverters.toScala(client.submit(JournalCurrentBlock()))
    
    // JournalClient
    def connect(address: String) {
      client.connect(new Address(address)).join()
    }
    
    def close() {
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
