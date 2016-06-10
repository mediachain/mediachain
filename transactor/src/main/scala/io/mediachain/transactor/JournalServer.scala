package io.mediachain.transactor

import io.atomix.catalyst.transport.Address
import io.atomix.copycat.server.CopycatServer
import io.mediachain.copycat.Server
import io.mediachain.datastore.PersistentDatastore

import scala.collection.JavaConversions._
import scala.io.StdIn
import scala.sys.process._


object JournalServer {

  def run(config: Config) {

    val rootdir = config.transactorDataDir.getAbsolutePath
    (s"mkdir -p $rootdir").!

    val copycatdir = rootdir + "/copycat"
    (s"mkdir $copycatdir").!

    val rockspath = rootdir + "/rocks.db"
    val address = config.listenAddress.asString
    val sslConfig = config.sslConfig

    val dynamoConfig = config.dynamoConfig
    val datastore = new PersistentDatastore(
      PersistentDatastore.Config(dynamoConfig, rockspath))

    datastore.start
    val server = Server.build(address, copycatdir, datastore, sslConfig)
    
    if (config.clusterAddresses.isEmpty) {
      server.bootstrap.join()
    } else {
      server.join(config.clusterAddresses.map {addr =>
        new Address(addr.asString)}
      ).join()
    }
    
    if (config.interactive) {
      println("Running with interactive console")
      interactiveLoop(server)
    } else {
      serverLoop(server)
    }
    datastore.close()
  }
  
  def serverLoop(server: CopycatServer) {
    while (server.isRunning) {Thread.sleep(1000)}
  }

  def interactiveLoop(server: CopycatServer) {
    print("> ")
    val next = StdIn.readLine()
    if (next != null) {
      val cmd = next.trim
      cmd match {
        case "shutdown" =>
          println("shutting down")
          server.shutdown.join()
        case "leave" =>
          println("leaving cluster")
          server.leave.join()
        case "" =>
          interactiveLoop(server)
        case what =>
          println(s"Unknown command ${what}; I only understand shutdown and leave")
          interactiveLoop(server)
      }
    } else {
      // stdin closed; shutdown
      server.shutdown.join()
    }
  }
}
