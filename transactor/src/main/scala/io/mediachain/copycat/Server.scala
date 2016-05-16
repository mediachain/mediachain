package io.mediachain.copycat

import java.io.File
import java.util.function.Supplier
import io.atomix.copycat.server.{CopycatServer, StateMachine => CopycatStateMachine}
import io.atomix.copycat.server.storage.{Storage, StorageLevel}
import io.atomix.catalyst.transport.{Address, NettyTransport}

import io.mediachain.protocol.Datastore._
import io.mediachain.copycat.StateMachine._

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
