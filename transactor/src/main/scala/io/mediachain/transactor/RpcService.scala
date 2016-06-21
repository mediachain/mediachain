package io.mediachain.transactor

object RpcService {
  import io.mediachain.copycat.{Client, TransactorService}
  import io.mediachain.datastore.DynamoDatastore
  import io.mediachain.util.Properties
  import io.grpc.Server
  import scala.concurrent.ExecutionContext
  import java.util.concurrent.Executors

  import org.slf4j.LoggerFactory

  val logger = LoggerFactory.getLogger(RpcService.getClass)

  def main(args: Array[String]) {
    if (args.length < 2) {
      println("arguments: config server-address ...")
      sys.exit(1)
    }
    
    val conf = Properties.load(args.head)
    val cluster = args.tail.toList
    run(conf, cluster)
  }
  
  def run(conf: Properties, cluster: List[String]) {
    val rpcPort = conf.getq("io.mediachain.transactor.rpc.port").toInt
    val ctldir = conf.getq("io.mediachain.transactor.rpc.control")
    val client = Client.build()
    logger.info(s"Connecting to cluster at $cluster...")
    client.connect(cluster)

    val threads = Math.max(4, Runtime.getRuntime.availableProcessors)
    implicit val ec = ExecutionContext.fromExecutor(Executors.newFixedThreadPool(threads))
    
    val datastoreConfig = DynamoDatastore.Config.fromProperties(conf)
    val datastore = new DynamoDatastore(datastoreConfig)

    val txService = new TransactorService(client, datastore)

    val server = TransactorService.createServer(txService, rpcPort)

    logger.info(s"started rpc service on port $rpcPort")
    server.start()
    serverControlLoop(ctldir, server, client)
  }
  
  def serverControlLoop(ctldir: String, server: Server, client: Client) {
    def shutdown(what: String) {
      logger.info("shutting down")
      client.close()
      server.shutdown()
      System.exit(0)
    }
    
    val ctl = ServerControl.build(ctldir, Map("shutdown" -> shutdown _))
    ctl.run
  }


  def run(config: Config) {
    run(config.conf, config.cluster)
  }
}


