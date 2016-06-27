package io.mediachain.transactor

// this is placed in io.mediachain.transactor to accompany the rest of the programs
object DatastoreRpcService {
  import io.mediachain.datastore.{DynamoDatastore, DatastoreService}
  import io.mediachain.util.Properties
  import io.grpc.Server
  import scala.concurrent.ExecutionContext
  import java.util.concurrent.Executors
  import org.slf4j.LoggerFactory
  
  val logger = LoggerFactory.getLogger(DatastoreRpcService.getClass)
  
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("arguments: config-file")
      sys.exit(1)
    }
    
    val conf = Properties.load(args.head)
    run(conf)
  }
  
  def run(conf: Properties) {
    val rpcPort = conf.getq("io.mediachain.datastore.rpc.port").toInt
    val ctldir = conf.getq("io.mediachain.datastore.rpc.control")
    val maxObjectSize = conf.getopt("io.mediachain.datastore.maxObjectSize") match {
      case Some(str) => str.toInt
      case None => DatastoreService.defaultMaxObjectSize
    }
      
    val datastoreConfig = DynamoDatastore.Config.fromProperties(conf)
    val datastore = new DynamoDatastore(datastoreConfig)
    
    // use a cached thread pool since all the threads do blocking ops
    implicit val ec = ExecutionContext.fromExecutor(
      Executors.newCachedThreadPool(),
      (e: Throwable) => logger.error("Error in asynchronous task", e)
    )
    val dsService = new DatastoreService(datastore, maxObjectSize)
    val server = DatastoreService.createServer(dsService, rpcPort)
    
    logger.info(s"started rpc service on port $rpcPort")
    server.start()
    serverControlLoop(ctldir, server)
  }
  
  def serverControlLoop(ctldir: String, server: Server) {
    def shutdown(what: String) {
      logger.info("shutting down")
      server.shutdown()
      System.exit(0)
    }
    
    val ctl = ServerControl.build(ctldir, Map("shutdown" -> shutdown _))
    ctl.run
  }
}
