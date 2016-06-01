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
    if (args.length != 2) {
      print("arguments: server-address rpc-service-port")
      sys.exit(1)
    }

    val serverAddress = args(0)
    val rpcPort = Integer.parseInt(args(1))

    val client = Client.build()
    logger.info(s"Connecting to server at $serverAddress...")
    client.connect(serverAddress)

    implicit val ec = ExecutionContext.global

    val txService = new TransactorService(client)

    TransactorService.createServerThread(txService,
      Executors.newSingleThreadExecutor(),
      rpcPort
    )

    println(s"started rpc service on port $rpcPort")
  }
}


