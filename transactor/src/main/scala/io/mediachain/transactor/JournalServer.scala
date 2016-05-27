package io.mediachain.transactor

import java.util.Properties
import java.io.FileInputStream
import com.amazonaws.auth.BasicAWSCredentials
import io.atomix.catalyst.transport.Address
import io.mediachain.copycat.{Server, Transport}
import io.mediachain.datastore.{PersistentDatastore, DynamoDatastore}
import scala.collection.JavaConversions._
import sys.process._

object JournalServer {
  def main(args: Array[String]) {
    if (args.length < 1) {
      println("Arguments: server-config-file [cluster-address ...]")
      System.exit(1)
    }
    
    val propfile = args.head
    val cluster = args.tail.toList
    val props = new Properties
    props.load(new FileInputStream(propfile))
    run(props, cluster)
  }
  
  def run(conf: Properties, cluster: List[String]) {
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
    val awsaccess = getq("io.mediachain.transactor.dynamo.awscreds.access")
    val awssecret = getq("io.mediachain.transactor.dynamo.awscreds.secret")
    val awscreds = new BasicAWSCredentials(awsaccess, awssecret)
    val dynamoBaseTable = getq("io.mediachain.transactor.dynamo.baseTable")
    val dynamoEndpoint = getopt("io.mediachain.transactor.dynamo.endpoint")
    val datastore = new PersistentDatastore(
      PersistentDatastore.Config(
        DynamoDatastore.Config(dynamoBaseTable, awscreds, dynamoEndpoint),
        rockspath))
    datastore.start
    val server = Server.build(address, copycatdir, datastore, sslConfig)
    
    if (cluster.isEmpty) {
      server.bootstrap().join()
    } else {
      server.join(cluster.map {addr => new Address(addr)}).join()
    }
    
    while (server.isRunning) {Thread.sleep(1000)}
  }
}
