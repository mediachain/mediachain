package io.mediachain.api

import java.util.UUID

import akka.actor.{Actor, Props}
import cats.data.Xor
import spray.routing.HttpService
import spray.http._
import org.json4s.DefaultFormats
import spray.httpx.Json4sJacksonSupport
import gremlin.scala._
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

object LSpaceServiceActor {
  def props(graphFactory: OrientGraphFactory): Props =
    Props(classOf[LSpaceServiceActor], graphFactory)
}

class LSpaceServiceActor(val graphFactory: OrientGraphFactory)
  extends Actor with LSpaceService {

  def actorRefFactory = context

  def receive = runRoute(baseRoute)
}

object JsonSupport extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats = DefaultFormats
}

trait LSpaceService extends HttpService {
  import JsonSupport._

  val graphFactory: OrientGraphFactory

  def withGraph[T](f: Graph => T): T = {
    val graph = graphFactory.getTx()
    val result = f(graph)
    graph.close()
    result
  }


  import operations.CanonicalQueries._
  val canonicalRoutes =
    pathPrefix("canonicals") {
      get {
        // GET "/canonicals"
        pathEnd {
          parameter("page" ? 0) { page =>
            complete {
              withGraph(listCanonicals(page))
            }
          }
        } ~
          pathPrefix(JavaUUID) { canonicalID: UUID =>
            // GET "/canonicals/some-canonical-id"
            pathEnd {
              parameter("with_raw" ? 0 ) { with_raw =>
                complete {
                  withGraph(canonicalWithID(canonicalID, with_raw == 1))
                }
              }
            } ~
              // GET "/canonicals/some-canonical-id/history
              path("history") {
                complete {
                  withGraph(historyForCanonical(canonicalID))
                }
              } ~
              // GET "/canonicals/persons-canonical-id/works"
              path("works") {
                complete {
                  withGraph(worksForPersonWithCanonicalID(canonicalID))
                }
              }
          }
      }
    }

  import operations.Translation._
  val translationRoutes =
    pathPrefix("translate") {
      post {
        path(Segment) { partnerName: String =>
          entity(as[String]) { rawMetadataString: String =>
            complete {
              val resultXor =
                translateRawMetadata(partnerName, rawMetadataString)

              resultXor match {
                case Xor.Left(err) =>
                  throw new RuntimeException(s"Translation error: $err")
                case Xor.Right(result) => result
              }
            }
          }
        }
      }
    }

  val ingestionRoutes =
    pathPrefix("ingest") {
      post {
        complete(???)
      }
    }

  val baseRoute =
    canonicalRoutes ~
      translationRoutes ~
      ingestionRoutes
}
