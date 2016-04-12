package io.mediachain.api

import java.util.UUID

import akka.actor.{Actor, Props}
import spray.routing.HttpService
import spray.http._
import io.mediachain.Types._
import org.json4s.{DefaultFormats, JObject}
import spray.httpx.Json4sJacksonSupport
import gremlin.scala._
import io.mediachain.{Query, Traversals}
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
import org.json4s.JsonAST.JString

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


  val PAGE_SIZE = 20
  def listCanonicals(page: Int): List[Canonical] =
    withGraph { graph =>
      val first = page * PAGE_SIZE
      val last = first + PAGE_SIZE

      graph.V.hasLabel[Canonical].toCC[Canonical]
        .range(first, last).toList
    }

  def canonicalWithID(canonicalID: UUID): Option[Canonical] =
    withGraph { graph =>
      Traversals.canonicalsWithUUID(graph.V, canonicalID)
        .toCC[Canonical]
        .headOption
    }

  def historyForCanonical(canonicalID: UUID): JObject = ???

  val baseRoute =
    pathPrefix("canonicals") {
      get {
        // GET "/canonicals"
        pathEnd {
          parameter("page" ? 0) { page =>
            complete(listCanonicals(page))
          }
        } ~
          pathPrefix(JavaUUID) { canonicalID: UUID =>
            // GET "/canonicals/some-canonical-id"
            pathEnd {
              complete(canonicalWithID(canonicalID))
            } ~
              // GET "/canonicals/some-canonical-id/history
              path("history") {
                complete(historyForCanonical(canonicalID))
              }
          }
      }
    }
}
