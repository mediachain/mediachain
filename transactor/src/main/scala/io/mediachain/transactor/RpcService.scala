package io.mediachain.transactor

object RpcService {
  import io.mediachain.copycat.{Client, TransactorService}
  import io.mediachain.datastore.DynamoDatastore
  import com.amazonaws.auth.BasicAWSCredentials
  import scala.concurrent.ExecutionContext
  import java.util.concurrent.Executors

  import org.slf4j.LoggerFactory

  val logger = LoggerFactory.getLogger(RpcService.getClass)

  def run(config: Config) {
    val rpcPort = config.listenAddress.port
    val cluster = config.clusterAddresses.map(_.asString).toList

    if (cluster.isEmpty) {
      throw new IllegalArgumentException(
        "No cluster address provided, can't start rpc service")
    }

    val client = Client.build()
    logger.info(s"Connecting to cluster at $cluster...")
    client.connect(cluster)

    implicit val ec = ExecutionContext.global
    // todo: dynamic
    val executor = Executors.newFixedThreadPool(4)
    val datastore = new DynamoDatastore(config.dynamoConfig)
    val txService = new TransactorService(client, executor, datastore)

    TransactorService.createServerThread(txService,
      Executors.newSingleThreadExecutor(),
      rpcPort
    )

    println(s"started rpc service on port $rpcPort")
  }
}


