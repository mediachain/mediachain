package io.mediachain.rpc

import java.util.logging.Logger

import gremlin.scala._
import io.grpc.{Server, ServerBuilder}
import io.mediachain.rpc.Services.{CanonicalWithRootRevision, _}
import io.mediachain.rpc.{Types => RPCTypes}
import io.mediachain.Types._
import io.mediachain.rpc.TypeConversions._
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import io.grpc.inprocess.InProcessServerBuilder
import io.mediachain.util.orient.MigrationHelper
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.language.existentials

sealed trait ServerContext {
  val executionContext: ExecutionContext
}

case class NetworkServerContext(
  port: Int = LSpaceServer.DEFAULT_PORT,
  executionContext: ExecutionContext = ExecutionContext.global
) extends ServerContext

case class InProcessServerContext(
  name: String = "LSpaceService-InProcess",
  executionContext: ExecutionContext = ExecutionContext.global
) extends ServerContext

object LSpaceServer {
  val DEFAULT_PORT = 50052

  private val logger = Logger.getLogger(classOf[LSpaceServer].getName)

  def main(args: Array[String]): Unit = {
    val server = new LSpaceServer(defaultGraphFactory)

    Runtime.getRuntime.addShutdownHook(new Thread() {
      override def run(): Unit = {
        System.err.println("*** shutting down gRPC server since JVM is shutting down")
        server.stop()
        System.err.println("*** server shut down")
      }
    })

    server.blockUntilShutdown()
  }

  def defaultGraphFactory: OrientGraphFactory =
    MigrationHelper.getMigratedGraphFactory() match {
      case Failure(err) => throw new IllegalStateException(
        "Unable to connect to L-SPACE graph db", err
      )
      case Success(factory) => factory
    }
}

class LSpaceServer(
  graphFactory: OrientGraphFactory,
  context: ServerContext = NetworkServerContext())
{
  private val server = setup()

  private def setup(): Server = {
    val (builder, msg): (ServerBuilder[_], String) =

    context match {
      case inProcess: InProcessServerContext => {
        (InProcessServerBuilder.forName(inProcess.name),
          s"Started in-process server: ${inProcess.name}")
      }

      case network: NetworkServerContext =>
        (ServerBuilder.forPort(network.port),
          s"Started network server: listening on localhost:${network.port}")
    }

    val service = new LSpaceServiceImpl(graphFactory, context.executionContext)
    val boundService = LSpaceServiceGrpc.bindService(service, context.executionContext)
    val server = builder.addService(boundService)
      .asInstanceOf[ServerBuilder[_]]
      .build().start()

    LSpaceServer.logger.info(msg)
    server
  }



  def stop(): Unit = {
    LSpaceServer.logger.info("Shutting down L-SPACE server")
    server.shutdown()
  }

  def blockUntilShutdown(): Unit = server.awaitTermination()


  private class LSpaceServiceImpl(
    graphFactory: OrientGraphFactory,
    implicit val executionContext: ExecutionContext
  ) extends LSpaceServiceGrpc.LSpaceService {

    def withGraph[T](f: Graph => T): T = {
      val graph = graphFactory.getTx()
      val result = f(graph)
      graph.close()
      result
    }


    import operations.CanonicalQueries
    override def listCanonicals(request: ListCanonicalsRequest)
    : Future[CanonicalList] = Future {
      withGraph {
        CanonicalQueries.listCanonicals(request.page.toInt)
      }
    }

    override def fetchCanonical(request: FetchCanonicalRequest)
    : Future[CanonicalWithRootRevision] = Future {
      val queryCanonical = Canonical(None, request.canonicalID)
      withGraph {
        CanonicalQueries.canonicalWithRootRevision(
          queryCanonical,
          withRaw = request.withRawMetadata)
      }.getOrElse {
        // FIXME: figure out how gRPC error handling is supposed to work
        throw new RuntimeException("Canonical not found")
      }
    }

    override def fetchCanonicalHistory(request: FetchCanonicalRequest)
    : Future[CanonicalWithHistory] = Future {
      withGraph {
        CanonicalQueries.historyForCanonical(request.canonicalID)
      }.getOrElse {
        // FIXME: figure out how gRPC error handling is supposed to work
        throw new RuntimeException("Canonical not found")
      }
    }

    override def listWorksForAuthor(request: WorksForAuthorRequest)
    : Future[WorksForAuthor] = Future {
      withGraph {
        CanonicalQueries.worksForPersonWithCanonicalID(request.authorCanonicalID)
      }.getOrElse {
        // FIXME: figure out how gRPC error handling is supposed to work
        throw new RuntimeException("Canonical not found")
      }
    }
  }
}
