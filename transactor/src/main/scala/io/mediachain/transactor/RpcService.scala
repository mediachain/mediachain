package io.mediachain.transactor

object RpcService {
  import io.mediachain.copycat.{Client, TransactorService}
  import io.mediachain.datastore.DynamoDatastore
  import com.amazonaws.auth.BasicAWSCredentials
  import scala.concurrent.ExecutionContext
  import java.util.concurrent.Executors

  import org.slf4j.LoggerFactory

  val logger = LoggerFactory.getLogger(RpcService.getClass)

  def main(args: Array[String]) {
    if (args.length < 2) {
      println("arguments: rpc-service-port server-address ...")
      sys.exit(1)
    }
    val rpcPort = Integer.parseInt(args.head)
    val cluster = args.tail.toList

    run(rpcPort, cluster)
  }

  def run(rpcPort: Int, cluster: List[String]) {
    val client = Client.build()
    logger.info(s"Connecting to cluster at $cluster...")
    client.connect(cluster)

    implicit val ec = ExecutionContext.global

    val txService = new TransactorService(client)

    TransactorService.createServerThread(txService,
      Executors.newSingleThreadExecutor(),
      rpcPort
    )

    println(s"started rpc service on port $rpcPort")
  }

  def run(config: Config): Unit = {
    val cluster = config.clusterAddresses.map(_.asString).toList
    run(config.listenAddress.port, cluster)
  }
}


