package io.mediachain.copycat

import java.nio.file.{Files, Path}

import io.atomix.copycat.server.CopycatServer
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.InMemoryDatastore
import io.mediachain.util.FileUtils
import io.mediachain.{BaseSpec, copycat}
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext}


object SeedingCopycatClientSpec extends BaseSpec
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

    val f = context.client.seed(chainWithDatastore.blockChain, chainWithDatastore.datastore)

    try {
      Await.result(f, 5.seconds) must beRightXor
    } finally {
      context.shutdown()
    }


    val seedObjectRefs = chainWithDatastore.datastore.store.keys
    seedObjectRefs.map(context.store.get) must contain(allOf(beSome[DataObject]))
  }

}


case class CopycatContext(
  server: CopycatServer,
  client: SeedingCopycatClient,
  store: Datastore,
  logdir: Path) {


  def startup(): Unit =
    server.bootstrap().join()



  def shutdown(): Unit = {
    if (server.isRunning) {
      server.shutdown().join()
    }
    FileUtils.rm_rf(logdir.toFile)
  }
}

object CopycatContext {
  def apply(address: String, blocksize: Int): CopycatContext = {
    val logdir = Files.createTempDirectory("mediachain-copycat-client-spec")
    val store = new InMemoryDatastore
    val server = copycat.Server.build(address, logdir.toAbsolutePath.toString, store)
    val client = new SeedingCopycatClient(address)(ExecutionContext.global)
    CopycatContext(server, client, store, logdir)
  }

}