package io.mediachain.rpc.client

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import io.mediachain.rpc.Services._
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
      // FIXME: don't hardcode this (only works on staging server)
      val canonical = client.fetchCanonical("0a84565b-2a43-4f6c-ba8f-6bd9802528b5")
      println(s"Received canonical: $canonical")
    } finally {
      client.shutdown()
    }
  }
}


class LSpaceClient (
  private val channel: ManagedChannel,
  private val blockingStub: LSpaceServiceBlockingStub
) {
  private[this] val logger = Logger.getLogger(classOf[LSpaceClient].getName)

  def shutdown(): Unit =
    channel.shutdown.awaitTermination(5, TimeUnit.SECONDS)

  def listCanonicals: CanonicalList = {
    logger.info("Requesting canonicals")
    try {
      val request = ListCanonicalsRequest(page = 0)
      blockingStub.listCanonicals(request)
    } catch {
      case e: StatusRuntimeException => {
        logger.warning(s"RPC request failed: ${e.getStatus}")
        CanonicalList()
      }
    }
  }

  def fetchCanonical(canonicalID: String): Option[CanonicalWithRootRevision] = {
    logger.info(s"Fetching canonical with id $canonicalID")
    try {
      val request = FetchCanonicalRequest(canonicalID = canonicalID)
      Some(blockingStub.fetchCanonical(request))
    } catch {
      case e: StatusRuntimeException => {
        logger.warning(s"RPC request failed: ${e.getStatus}")
        None
      }
    }
  }
}
