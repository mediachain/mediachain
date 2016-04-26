package io.mediachain.rpc.client

import java.util.concurrent.TimeUnit
import java.util.logging.Logger

import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import io.mediachain.protos.Services._
import io.mediachain.protos.Services.LSpaceServiceGrpc.LSpaceServiceBlockingStub
import io.mediachain.rpc.RPCError
import io.mediachain.rpc.TypeConversions._
import cats.data.Xor


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

  def tryRPCRequest[Response](f: => Response): Xor[RPCError, Response] = {
    try Xor.Right(f)
    catch {
      case e: StatusRuntimeException => {
        logger.warning(s"RPC request failed: ${e.getStatus}")
        val err = RPCError.fromException(e)
        Xor.Left(err)
      }
      case e: Throwable => throw e
    }
  }

  def listCanonicals(page: Int = 0)
  : Xor[RPCError, CanonicalList] =
    tryRPCRequest {
      logger.info("Requesting canonicals")
      val request = ListCanonicalsRequest(page = page.toLong)
      blockingStub.listCanonicals(request)
    }

  def fetchCanonical(canonicalID: String, withRawMetadata: Boolean = false)
  : Xor[RPCError, CanonicalWithRootRevision] =
    tryRPCRequest {
      logger.info(s"Fetching canonical with id $canonicalID")
      val request = FetchCanonicalRequest(
        canonicalID = canonicalID,
        withRawMetadata = withRawMetadata)
      blockingStub.fetchCanonical(request)
    }


  def fetchHistoryForCanonical(canonicalID: String)
  : Xor[RPCError, CanonicalWithHistory] =
    tryRPCRequest {
      logger.info(s"Fetching history for canonical with id $canonicalID")
      val request = FetchCanonicalRequest(canonicalID = canonicalID)
      blockingStub.fetchCanonicalHistory(request)
    }


  def listWorksForAuthorWithCanonicalID(canonicalID: String)
  : Xor[RPCError, WorksForAuthor] =
    tryRPCRequest {
      logger.info(s"Fetching works for author with canonical id $canonicalID")
      val request = WorksForAuthorRequest(authorCanonicalID = canonicalID)
      blockingStub.listWorksForAuthor(request)
    }

}
