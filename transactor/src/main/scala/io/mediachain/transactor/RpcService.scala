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

    val dynamoEndpoint = sys.env.get("DYNAMO_ENDPOINT_URL")
    val dynamoBaseTable = sys.env.getOrElse("DYNAMO_MEDIACHAIN_TABLE",
      "mediachain")

    val awsAccessKey = sys.env.getOrElse("AWS_ACCESS_KEY",
      "Using dummy AWS key. If not using local dynamo-db," +
      "make sure to set the AWS_ACCESS_KEY env variable."
    )

    val awsSecretKey = sys.env.getOrElse("AWS_SECRET_KEY",
      "Using dummy AWS key. If not using local dynamo-db," +
        "make sure to set the AWS_SECRET_KEY env variable."
    )

    val creds = new BasicAWSCredentials(awsAccessKey, awsSecretKey)
    val datastore = new DynamoDatastore(DynamoDatastore.Config(
      dynamoBaseTable, creds, dynamoEndpoint
    ))

    implicit val ec = ExecutionContext.global

    val txService = new TransactorService(client, datastore,
      executionContext = ec)

    TransactorService.createServerThread(txService,
      Executors.newSingleThreadExecutor(),
      rpcPort
    )

    println(s"started rpc service on port $rpcPort")
    while (true) {
      Thread.sleep(1000)
    }
  }
}


