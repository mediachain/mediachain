package io.mediachain.rpc.client

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import io.mediachain.rpc.Services.{LSpaceServiceGrpc, ListCanonicalsRequest}
import io.mediachain.rpc.Services.LSpaceServiceGrpc.LSpaceServiceBlockingStub
import io.mediachain.Types._
import io.mediachain.rpc.TypeConversions._

object LSpaceClient {
  def apply(host: String, port: Int): LSpaceClient = {
    val channel = ManagedChannelBuilder.forAddress(host, port)
      .usePlaintext(true).build

    val blockingStub = LSpaceServiceGrpc.blockingStub(channel)
    new LSpaceClient(channel, blockingStub)
  }

  def main(args: Array[String]): Unit = {
    val client = LSpaceClient("localhost", 50052)
    try {
      val canonicals = client.listCanonicals
      println("Received canonicals: ")
      canonicals.foreach(c => println(c.canonicalID))
    } finally {
      client.shutdown()
    }
  }
}


class LSpaceClient private(
  private val channel: ManagedChannel,
  private val blockingStub: LSpaceServiceBlockingStub
) {
  private[this] val logger = Logger.getLogger(classOf[LSpaceClient].getName)

  def shutdown(): Unit =
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)

  def listCanonicals: Seq[Canonical] = {
    logger.info("Requesting canonicals")
    try {
      val request = ListCanonicalsRequest()
      val response = blockingStub.listCanonicals(request)
      response.canonicals.map(_.fromRPC)
    } catch {
      case e: StatusRuntimeException => {
        logger.warning(s"RPC request failed: ${e.getStatus}")
        List()
      }
    }
  }
}
