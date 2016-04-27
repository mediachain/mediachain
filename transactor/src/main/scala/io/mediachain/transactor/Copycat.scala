package io.mediachain.transactor

import java.io.File
import java.util.function.Supplier

import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.{CopycatServer, StateMachine => CopycatStateMachine}
import io.atomix.copycat.server.storage.{Storage, StorageLevel}
import io.atomix.catalyst.transport.{Address, NettyTransport}
import io.atomix.catalyst.serializer.Serializer
import io.mediachain.transactor.JsonSerialization.CopycatSerializer

object Copycat {
  import io.mediachain.transactor.StateMachine.JournalStateMachine
  import io.mediachain.transactor.Types.Datastore

  object Server {
    def build(address: Address, logdir: String, datastore: Datastore): CopycatServer = {
      def stateMachineSupplier() = {
        new Supplier[CopycatStateMachine] {
          override def get: CopycatStateMachine = {
            new JournalStateMachine(datastore)
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
  
  object Client {
    def build(): CopycatClient = {
      val client = CopycatClient.builder()
                    .withTransport(NettyTransport.builder()
                                    .withThreads(2)
                                    .build())
                    .build()
      Serializers.register(client.serializer)
      client
    }
    
  }

  object Serializers {
    def register(serializer: Serializer) {
      JsonSerialization.copycatSerializers.foreach { typeSerializer =>
        serializer.register(typeSerializer.serializableClass, typeSerializer.getClass)
      }
    }
  }


}
