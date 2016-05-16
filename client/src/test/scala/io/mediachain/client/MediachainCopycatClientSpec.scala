package io.mediachain.client

import java.nio.file.{Files, Path}

import cats.data.Xor
import io.atomix.copycat.server.CopycatServer
import io.mediachain.BaseSpec
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.InMemoryDatastore
import io.mediachain.protocol.Transactor.{JournalCommitError, JournalError, JournalListener}
import io.mediachain.copycat
import io.mediachain.copycat.SeedingCopycatClient
import io.mediachain.copycat.Client.{ClientState, ClientStateListener}
import io.mediachain.util.FileUtils
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future, Promise}

case class CopycatContext(
  server: CopycatServer,
  store: Datastore,
  logdir: Path) {


  def startup(): Unit =
    server.bootstrap().join()



  def shutdown(): Unit = {
    server.shutdown().join()
    FileUtils.rm_rf(logdir.toFile)
  }
}

object CopycatContext {
  def apply(address: String, blocksize: Int): CopycatContext = {
    val logdir = Files.createTempDirectory("mediachain-copycat-client-spec")
    val store = new InMemoryDatastore
    val server = copycat.Server.build(address, logdir.toAbsolutePath.toString, store)
    CopycatContext(server, store, logdir)
  }

}

object MediachainCopycatClientSpec extends BaseSpec
  with ForEach[CopycatContext]
{

  def is =
    s2"""
        - seeds copycat cluster with generated blockchain $seedsBlockchain
      """

  val blockSize = 6


  override protected def foreach[R](f: (CopycatContext) => R)
    (implicit ev: AsResult[R]): Result = {
    val context = CopycatContext("127.0.0.1:12345", blockSize)
    context.startup()
    val result = AsResult(f(context))
    context.shutdown()
    result
  }

  def seedsBlockchain = { context: CopycatContext =>
    import io.mediachain.protocol.JournalBlockGenerators.genBlockChain

    val chainWithDatastore = genBlockChain(1, blockSize).sample.getOrElse(
      throw new Exception("Can't generate testing blockchain")
    )

    val seedingClient = new SeedingCopycatClient("127.0.0.1:12345")(ExecutionContext.global)
    val f = seedingClient.seed(chainWithDatastore.blockChain, chainWithDatastore.datastore)

    try {
      Await.result(f, 5.seconds) must beRightXor
    } finally {
      seedingClient.client.close()
      context.shutdown()
    }


    val seedObjectRefs = chainWithDatastore.datastore.store.keys
    seedObjectRefs.map(context.store.get) must contain(beSome[DataObject])
  }

}
