package io.mediachain.transactor

import io.atomix.copycat.client.CopycatClient
import io.atomix.copycat.server.CopycatServer
import io.atomix.catalyst.transport.Address

case class DummyContext(
  server: CopycatServer, 
  client: CopycatClient, 
  store: Types.Datastore,
  logdir: String
)

object DummyContext {
  def setup(srvaddr: String, logdir: String) = {
    println("*** SETUP DUMMY COPYCAT CONTEXT")
    setupLogdir(logdir)
    val address = new Address(srvaddr)
    val store = new Dummies.DummyStore
    val server = Copycat.Server.build(address, logdir, store)
    server.bootstrap().join()
    val client = Copycat.Client.build()
    client.connect(address).join()
    DummyContext(server, client, store, logdir)
  }
  
  def setupLogdir(logdir: String) {
    import sys.process._
    (s"rm -rf $logdir").!
    (s"mkdir -p $logdir").!
  }
  
  def cleanupLogdir(logdir: String) {
    import sys.process._
    (s"rm -rf $logdir").!
  }
  
  def shutdown(context: DummyContext) {
    println("*** SHUT DOWN DUMMY COPYCAT CONTEXT")
    context.client.close().join()
    context.server.shutdown().join()
    cleanupLogdir(context.logdir)
  }
}
