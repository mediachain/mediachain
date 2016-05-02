package io.mediachain.transactor

import io.atomix.copycat.server.CopycatServer

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
