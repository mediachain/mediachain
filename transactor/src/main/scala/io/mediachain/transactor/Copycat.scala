package io.mediachain.transactor

import java.io.File
import java.util.function.{Consumer, Supplier}
import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.{CopycatServer, StateMachine => CopycatStateMachine}
import io.atomix.copycat.server.storage.{Storage, StorageLevel}
import io.atomix.catalyst.transport.{Address, NettyTransport}
import io.atomix.catalyst.serializer.Serializer

import scala.compat.java8.FutureConverters

object Copycat {
  import io.mediachain.transactor.StateMachine._
  import io.mediachain.transactor.Types._

  object Server {
    def build(address: Address, logdir: String, datastore: Datastore,
              blocksize: Int = StateMachine.JournalBlockSize): CopycatServer = {
      def stateMachineSupplier() = {
        new Supplier[CopycatStateMachine] {
          override def get: CopycatStateMachine = {
            new JournalStateMachine(datastore, blocksize)
          }
        }
      }
      
      val server = CopycatServer.builder(address)
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
    def insert(rec: CanonicalRecord) =
      FutureConverters.toScala(client.submit(JournalInsert(rec)))

    def update(ref: Reference, cell: ChainCell) =
      FutureConverters.toScala(client.submit(JournalUpdate(ref, cell)))
    
    def lookup(ref: Reference) = 
      FutureConverters.toScala(client.submit(JournalLookup(ref)))
    
    def currentBlock =
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
    def register(serializer: Serializer) {
      // XXX temporary to enable blancket use of Serializables
      // TODO register all used classes with the serializer
      serializer.disableWhitelist()
    }
  }
  
  
}
