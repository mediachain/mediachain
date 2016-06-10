package io.mediachain.transactor

/** These entrypoint functions serve as simple starters for running the system
  * in a *development* environment.
  *
  */
object Entrypoints {
  import io.mediachain.datastore.DynamoDatastore
  import com.amazonaws.auth.BasicAWSCredentials

  private val datastoreConfig = DynamoConfig(
    "Mediachain",
    Some(Endpoint("localhost:8000"))
  )

  def startTransactorService(copycatAddr: String, listen: Int): Unit = {
    import io.mediachain.copycat.Client
    import io.mediachain.copycat.TransactorService
    import java.util.concurrent.Executors
    import scala.concurrent.ExecutionContext.Implicits.global

    val client = Client.build()
    client.connect(copycatAddr)

    val executor = Executors.newFixedThreadPool(4)
    val datastore = new DynamoDatastore(datastoreConfig)
    val service = new TransactorService(client, executor, datastore)

    TransactorService.createServerThread(service, executor, listen)
  }

  def startCopycatService(listenAddr: String,
                          logDir: String = "tmp"): Unit = {
    import io.mediachain.copycat.Server

    val datastore = new DynamoDatastore(datastoreConfig)
    val server = Server.build(listenAddr, logDir, datastore)

    server.bootstrap
  }
}

