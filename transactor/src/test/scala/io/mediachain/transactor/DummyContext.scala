package io.mediachain.transactor

import io.atomix.copycat.server.CopycatServer
import io.atomix.catalyst.transport.Address

case class DummyContext(
  server: CopycatServer, 
  client: Types.JournalClient, 
  store: Dummies.DummyStore,
  logdir: String
)

object DummyContext {
  def setup(address: String, blocksize: Int = StateMachine.JournalBlockSize) = {
    println("*** SETUP DUMMY COPYCAT CONTEXT")
    val logdir = setupLogdir()
    val store = new Dummies.DummyStore
    val server = Copycat.Server.build(address, logdir, store, blocksize)
    server.bootstrap().join()
    val client = Copycat.Client.build()
    client.connect(address)
    DummyContext(server, client, store, logdir)
  }
  
  def setupLogdir() = {
    import sys.process._
    val logdir = "mktemp -d".!!
    (s"mkdir -p $logdir").!
    logdir
  }
  
  def cleanupLogdir(logdir: String) {
    import sys.process._
    (s"rm -rf $logdir").!
  }
  
  def shutdown(context: DummyContext) {
    println("*** SHUT DOWN DUMMY COPYCAT CONTEXT")
    context.client.close()
    context.server.shutdown().join()
    cleanupLogdir(context.logdir)
  }
}

case class DummyClusterContext(
  dummies: Array[DummyContext]
)

object DummyClusterContext {
  def setup(address1: String, address2: String, address3: String,
            blocksize: Int = StateMachine.JournalBlockSize) = {
    println("*** SETUP DUMMY COPYCAT CLUSTER")
    val store1 = new Dummies.DummyStore
    val logdir1 = DummyContext.setupLogdir()
    val server1 = Copycat.Server.build(address1, logdir1, store1, blocksize)
    server1.bootstrap().join()
    val store2 = new Dummies.DummyStore
    val logdir2 = DummyContext.setupLogdir()
    val server2 = Copycat.Server.build(address2, logdir2, store2, blocksize)
    server2.join(new Address(address1)).join()
    val store3 = new Dummies.DummyStore
    val logdir3 = DummyContext.setupLogdir()
    val server3 = Copycat.Server.build(address3, logdir3, store3, blocksize)
    server3.join(new Address(address1), new Address(address2)).join()
    val client1 = Copycat.Client.build()
    client1.connect(address1)
    val client2 = Copycat.Client.build()
    client2.connect(address2)
    val client3 = Copycat.Client.build()
    client3.connect(address3)
    DummyClusterContext(Array(DummyContext(server1, client1, store1, logdir1),
                              DummyContext(server2, client2, store2, logdir2),
                              DummyContext(server3, client3, store3, logdir3)))
  }
  
  def shutdown(context: DummyClusterContext) {
    println("*** SHUTDOWN DUMMY COPYCAT CLUSTER")
    // close all clients before shutting down any servers
    context.dummies.foreach(_.client.close())
    context.dummies.foreach(_.server.shutdown().join())
    context.dummies.foreach(dummy => DummyContext.cleanupLogdir(dummy.logdir))
  }
}
