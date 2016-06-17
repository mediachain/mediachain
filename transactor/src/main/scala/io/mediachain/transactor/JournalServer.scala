package io.mediachain.transactor

import java.util.Properties
import java.io.FileInputStream
import com.amazonaws.auth.BasicAWSCredentials
import org.slf4j.{Logger, LoggerFactory}
import io.atomix.catalyst.transport.Address
import io.atomix.copycat.server.CopycatServer
import io.mediachain.copycat.{Server, Transport}
import io.mediachain.datastore.{PersistentDatastore, DynamoDatastore}
import scala.io.StdIn
import scala.collection.JavaConversions._
import sys.process._

object JournalServer {
  val logger = LoggerFactory.getLogger("io.mediachain.transactor.JournalServer")

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
    val ctldir = rootdir + "/ctl"
    (s"mkdir $ctldir").!
    (s"touch ${ctldir}/shutdown").!
    (s"touch ${ctldir}/leave").!
    val copycatdir = rootdir + "/copycat"
    (s"mkdir $copycatdir").!
    val rockspath = rootdir + "/rocks.db"
    val address = getq("io.mediachain.transactor.server.address")
    val sslConfig = Transport.SSLConfig.fromProperties(conf)
    val dynamoConfig = DynamoDatastore.Config.fromProperties(conf)
    val datastore = new PersistentDatastore(PersistentDatastore.Config(dynamoConfig, rockspath))
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
    val ctl = new ServerControl(ctldir, commands)
    ctl.run
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
}
