package io.mediachain.rpc

import java.util.logging.Logger

import gremlin.scala._
import io.grpc.{Server, ServerBuilder}
import io.mediachain.rpc.Services.{CanonicalWithRootRevision, _}
import io.mediachain.rpc.{Types => RPCTypes}
import io.mediachain.Types._
import io.mediachain.rpc.TypeConversions._
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import io.mediachain.util.orient.MigrationHelper

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

object LSpaceServer {
  private val port = 50052
  private val logger = Logger.getLogger(classOf[LSpaceServer].getName)

  def main(args: Array[String]): Unit = {
    if (ODatabaseRecordThreadLocal.INSTANCE == null) {
      sys.error("This should never be reached. " +
      "Accessing ODatabaseRecordThreadLocal.INSTANCE prevents an " +
      "initialization error.  see: " +
      "https://github.com/orientechnologies/orientdb/issues/5146")
    }

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

    lazy val graphFactory = MigrationHelper.getMigratedGraphFactory() match {
      case Failure(err) => throw new IllegalStateException(
        "Unable to connect to L-SPACE graph db", err
      )
      case Success(factory) => factory
    }


    def getGraph: Graph = graphFactory.getTx


    /// FIXME: implement with code from spray branch
    override def listCanonicals(request: ListCanonicalsRequest)
    : Future[CanonicalList] = ???

    override def fetchCanonical(request: FetchCanonicalRequest)
    : Future[CanonicalWithRootRevision] = ???

    override def fetchCanonicalHistory(request: FetchCanonicalRequest)
    : Future[CanonicalWithHistory] = ???

    override def listWorksForAuthor(request: WorksForAuthorRequest)
    : Future[WorksForAuthor] = ???
  }
}
