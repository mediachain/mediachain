package io.mediachain.transactor

import java.util.Properties
import java.io.FileInputStream
import com.amazonaws.auth.BasicAWSCredentials
import io.atomix.catalyst.transport.Address
import io.atomix.copycat.server.CopycatServer
import io.mediachain.copycat.{Server, Transport}
import io.mediachain.datastore.{PersistentDatastore, DynamoDatastore}
import scala.io.StdIn
import scala.collection.JavaConversions._
import sys.process._

object JournalServer {
  def main(args: Array[String]) {
    val (interactive, config, cluster) = parseArgs(args)
    val props = new Properties
    props.load(new FileInputStream(config))
    run(interactive, props, cluster)
  }
  
  def parseArgs(args: Array[String]) = {
    args.toList match {
      case "-i" :: config :: cluster =>
        (true, config, cluster.toList)
      case config :: cluster =>
        (false, config, cluster.toList)
      case _ =>
        throw new RuntimeException("Expected arguments: [-i] config [cluster-address ...]")
    }
  }

  def run(interactive: Boolean, conf: Properties, cluster: List[String]) {
    def getq(key: String): String =
      getopt(key).getOrElse {throw new RuntimeException("Missing configuration property: " + key)}
    
    def getopt(key: String): Option[String] =
      Option(conf.getProperty(key))
    
    val rootdir = getq("io.mediachain.transactor.server.rootdir")
    (s"mkdir -p $rootdir").!
    val copycatdir = rootdir + "/copycat"
    (s"mkdir $copycatdir").!
    val rockspath = rootdir + "/rocks.db"
    val address = getq("io.mediachain.transactor.server.address")
    val sslConfig = Transport.SSLConfig.fromProperties(conf)
    val dynamoBaseTable = getq("io.mediachain.transactor.dynamo.baseTable")
    val dynamoEndpoint = getopt("io.mediachain.transactor.dynamo.endpoint")
    val datastore = new PersistentDatastore(
      PersistentDatastore.Config(
        DynamoDatastore.Config(dynamoBaseTable, dynamoEndpoint),
        rockspath))
    datastore.start
    val server = Server.build(address, copycatdir, datastore, sslConfig)
    
    if (cluster.isEmpty) {
      server.bootstrap.join()
    } else {
      server.join(cluster.map {addr => new Address(addr)}).join()
    }
    
    if (interactive) {
      println("Running with interactive console")
      interactiveLoop(server)
    } else {
      serverLoop(server)
    }
    datastore.close()
  }

  def run(config: Config) {
    val props = new Properties()
    props.setProperty("io.mediachain.transactor.server.rootdir",
      config.transactorDataDir.getAbsolutePath)
    props.setProperty("io.mediachain.transactor.server.address",
      config.listenAddress.asString)
    props.setProperty("io.mediachain.transactor.dynamo.baseTable",
      config.dynamoConfig.baseTable)
    config.dynamoConfig.endpoint.foreach { endpoint =>
      props.setProperty("io.mediachain.transactor.dynamo.endpoint",
        endpoint)
    }
    val cluster = config.clusterAddresses.map(_.asString).toList
    run(config.interactive, props, cluster)
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
