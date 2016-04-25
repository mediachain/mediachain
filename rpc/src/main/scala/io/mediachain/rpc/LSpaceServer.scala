package io.mediachain.rpc

import java.util.logging.Logger

import cats.data.Xor
import gremlin.scala._
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.{Server, ServerBuilder}
import io.mediachain.Types._
import io.mediachain.protos.Services._
import io.mediachain.util.Env
import io.mediachain.util.orient.MigrationHelper
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

import scala.concurrent.{ExecutionContext, Future}
import scala.language.existentials
import scala.util.{Failure, Success}

sealed trait ServerContext {
  val executionContext: ExecutionContext
}

case class NetworkServerContext(
  port: Int = LSpaceServer.LISTEN_PORT,
  executionContext: ExecutionContext = ExecutionContext.global
) extends ServerContext

case class InProcessServerContext(
  name: String = "LSpaceService-InProcess",
  executionContext: ExecutionContext = ExecutionContext.global
) extends ServerContext

object LSpaceServer {
  val PORT_ENV_VAR = "LSPACE_LISTEN_PORT"
  val DEFAULT_PORT = 50052
  val LISTEN_PORT = Env.getInt(PORT_ENV_VAR).getOrElse(DEFAULT_PORT)

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
        throw RPCError.NotFound(
          s"Cannot fetch non-existent canonical ${request.canonicalID}"
        ).asException
      }
    }

    override def fetchCanonicalHistory(request: FetchCanonicalRequest)
    : Future[CanonicalWithHistory] = Future {
      withGraph {
        CanonicalQueries.historyForCanonical(request.canonicalID)
      }.getOrElse {
        throw RPCError.NotFound(
          s"Cannot fetch history for non-existent canonical ${request.canonicalID}"
        ).asException
      }
    }

    override def listWorksForAuthor(request: WorksForAuthorRequest)
    : Future[WorksForAuthor] = Future {
      withGraph {
        CanonicalQueries.worksForPersonWithCanonicalID(request.authorCanonicalID)
      }.getOrElse {
        throw RPCError.NotFound(
          s"Cannot fetch works for non-existent canonical ${request.authorCanonicalID}"
        ).asException
      }
    }


    // Mutations
    import operations.Merging
    override def mergeCanonicals(request: MergeCanonicalsRequest)
    : Future[MergeCanonicalsResponse] = Future {
      val resultXor = withGraph {
        Merging.mergeCanonicals(
          request.childCanonicalID,
          request.parentCanonicalID)
      }

      resultXor match {
        case Xor.Left(err) =>
          throw RPCError.FailedPrecondition(s"Error merging canonicals: $err")
            .asException

        case Xor.Right(result) => result
      }
    }
  }
}
