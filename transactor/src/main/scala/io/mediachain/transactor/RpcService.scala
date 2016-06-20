package io.mediachain.transactor

object RpcService {
  import io.mediachain.copycat.{Client, TransactorService}
  import io.mediachain.datastore.DynamoDatastore
  import io.mediachain.util.Properties
  import com.amazonaws.auth.BasicAWSCredentials
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
    val ctldir = conf.getq("io.mediachain.transactor.server.rootdir" + "/ctl")
    val client = Client.build()
    logger.info(s"Connecting to cluster at $cluster...")
    client.connect(cluster)

    implicit val ec = ExecutionContext.global

    val txService = new TransactorService(client)

    TransactorService.createServerThread(txService,
      Executors.newSingleThreadExecutor(),
      rpcPort
    )

    logger.info(s"started rpc service on port $rpcPort")
    serverControlLoop(ctldir, client)
  }
  
  def serverControlLoop(ctldir: String, client: Client) {
    def shutdown(what: String) {
      logger.info("shutting down")
      client.close()
      System.exit(0)
    }
    
    val ctl = ServerControl.build(ctldir, Map("shutdown" -> shutdown _))
    ctl.run
  }

  def run(config: Config): Unit = {
    throw new RuntimeException("FIXME")
  }
}


