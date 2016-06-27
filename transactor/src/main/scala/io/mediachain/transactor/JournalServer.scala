package io.mediachain.transactor

import com.amazonaws.auth.BasicAWSCredentials
import org.slf4j.{Logger, LoggerFactory}
import io.atomix.catalyst.transport.Address
import io.atomix.copycat.server.CopycatServer
import io.mediachain.copycat.{Server, Transport}
import io.mediachain.datastore.{PersistentDatastore, DynamoDatastore}
import io.mediachain.util.Properties
import scala.io.StdIn
import scala.collection.JavaConversions._
import sys.process._

object JournalServer {
  val logger = LoggerFactory.getLogger("io.mediachain.transactor.JournalServer")

  def main(args: Array[String]) {
    val (config, cluster) = parseArgs(args)
    val props = Properties.load(config)
    run(props, cluster)
  }
  
  def parseArgs(args: Array[String]) = {
    args.toList match {
      case config :: cluster =>
        (config, cluster.toList)
      case _ =>
        throw new RuntimeException("Expected arguments: config [cluster-address ...]")
    }
  }

  def run(conf: Properties, cluster: List[String]) {
    val rootdir = conf.getq("io.mediachain.transactor.server.rootdir")
    (s"mkdir -p $rootdir").!
    val ctldir = rootdir + "/ctl/JournalServer"
    val copycatdir = rootdir + "/copycat"
    (s"mkdir $copycatdir").!
    val address = conf.getq("io.mediachain.transactor.server.address")
    val sslConfig = Transport.SSLConfig.fromProperties(conf)
    val datastoreConfig = PersistentDatastore.Config.fromProperties(conf)
    val datastore = new PersistentDatastore(datastoreConfig)
    val server = Server.build(address, copycatdir, datastore, sslConfig)

    datastore.start    
    if (cluster.isEmpty) {
      server.bootstrap.join()
    } else {
      server.join(cluster.map {addr => new Address(addr)}).join()
    }
    
    serverControlLoop(ctldir, server, datastore)
  }
  
  def serverControlLoop(ctldir: String, 
                        server: CopycatServer, 
                        datastore: PersistentDatastore) {
    def shutdown(what: String) {
      logger.info("Shutting down server")
      server.shutdown.join()
      quit()
    }
    
    def leave(what: String) {
      logger.info("Leaving the cluster")
      server.leave.join()
      quit()
    }
    
    def quit() {
      datastore.close()
      System.exit(0)
    }
    
    val commands = Map(
      "shutdown" -> shutdown _,
      "leave" -> leave _
    )
    val ctl = ServerControl.build(ctldir, commands)
    ctl.run
  }

  def run(config: Config) {
    run(config.conf, config.cluster)
  }
}
