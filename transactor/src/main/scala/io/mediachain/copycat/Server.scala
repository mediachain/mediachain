package io.mediachain.copycat

import java.io.File
import java.util.function.Supplier
import java.time.Duration 
import io.atomix.copycat.server.{CopycatServer, StateMachine => CopycatStateMachine}
import io.atomix.copycat.server.storage.{Storage, StorageLevel}
import io.atomix.catalyst.transport.Address

import io.mediachain.protocol.Datastore._
import io.mediachain.copycat.StateMachine._

object Server {
  def build(address: String, logdir: String, datastore: Datastore,
            sslConfig: Option[Transport.SSLConfig] = None,
            blocksize: Int = StateMachine.JournalBlockSize)
  : CopycatServer = {
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
      .withTransport(Transport.build(4, sslConfig))
      .withElectionTimeout(Duration.ofSeconds(3))
      .withHeartbeatInterval(Duration.ofSeconds(1))
      .withSessionTimeout(Duration.ofSeconds(5))
      .build()
    Serializers.register(server.serializer)
    server
  }
}

