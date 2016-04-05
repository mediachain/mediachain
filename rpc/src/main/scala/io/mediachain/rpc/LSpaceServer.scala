package io.mediachain.rpc

import java.util.logging.Logger

import io.grpc.{Server, ServerBuilder}
import io.mediachain.rpc.Services.{CanonicalList, LSpaceServiceGrpc, ListCanonicalsRequest}
import io.mediachain.rpc.{Types => RPCTypes}

import scala.concurrent.{ExecutionContext, Future}

object LSpaceServer {
  private val port = 50052
  private val logger = Logger.getLogger(classOf[LSpaceServer].getName)

  def main(args: Array[String]): Unit = {
    val server = new LSpaceServer(ExecutionContext.global)
    server.start()
    server.blockUntilShutdown()
  }
}

class LSpaceServer(executionContext: ExecutionContext) { self =>
  private[this] var server: Server = null

  private def start(): Unit = {
    val service = new LSpaceServiceImpl
    server = ServerBuilder.forPort(LSpaceServer.port)
      .addService(LSpaceServiceGrpc.bindService(service, executionContext))
      .build.start

    LSpaceServer.logger
      .info(s"Server started, listening on localhost:${LSpaceServer.port}")

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        self.stop()
        System.err.println("*** server shut down")
      }
    })
  }

  private def stop(): Unit = {
    if (server != null) {
      server.shutdown()
    }
  }

  private def blockUntilShutdown(): Unit = {
    if (server != null) {
      server.awaitTermination()
    }
  }

  private class LSpaceServiceImpl extends LSpaceServiceGrpc.LSpaceService {
    override def listCanonicals(request: ListCanonicalsRequest)
    : Future[CanonicalList] = {

      ???
    }

    override def subtreeForCanonical(request: RPCTypes.Canonical)
    : Future[RPCTypes.Graph] = {
      ???
    }
  }
}
