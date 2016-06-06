package io.mediachain.transactor

/** These entrypoint functions serve as simple starters for running the system
  * in a *development* environment.
  *
  */
object Entrypoints {
  def startTransactorService(copycatAddr: String, listen: Int): Unit = {
    import io.mediachain.copycat.Client
    import io.mediachain.copycat.TransactorService
    import java.util.concurrent.Executors
    import scala.concurrent.ExecutionContext.Implicits.global

    val client = Client.build()
    client.connect(copycatAddr)

    val service = new TransactorService(client)
    val executor = Executors.newSingleThreadExecutor()

    TransactorService.createServerThread(service, executor, listen)
  }

  def startCopycatService(listenAddr: String,
                          logDir: String = "tmp"): Unit = {
    import io.mediachain.copycat.Server
    import io.mediachain.datastore.DynamoDatastore
    import com.amazonaws.auth.BasicAWSCredentials

    val datastoreConfig = DynamoDatastore.Config(
      "Mediachain",
      new BasicAWSCredentials("", ""),
      Some("http://localhost:8000")
    )
    val datastore = new DynamoDatastore(datastoreConfig)
    val server = Server.build(listenAddr, logDir, datastore)

    server.bootstrap
  }
}

