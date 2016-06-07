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
    
    val client = Client.build()
    logger.info(s"Connecting to cluster at $cluster...")
    client.connect(cluster)

    implicit val ec = ExecutionContext.global
    // todo: dynamic
    val executor = Executors.newFixedThreadPool(4)
    val datastoreConfig = DynamoDatastore.Config(
      "Mediachain",
      new BasicAWSCredentials("", ""),
      Some("http://localhost:8000")
    )
    val datastore = new DynamoDatastore(datastoreConfig)

    val txService = new TransactorService(client, executor, datastore)

    TransactorService.createServerThread(txService,
      Executors.newSingleThreadExecutor(),
      rpcPort
    )

    println(s"started rpc service on port $rpcPort")
  }
}


