package io.mediachain.api

import akka.actor.{Actor, Props}
import spray.routing.HttpService
import spray.http._
import io.mediachain.Types._
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

  def receive = runRoute(helloRoute)
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

  val helloRoute =
    path("") {
      get {
        complete {
          withGraph { graph =>
            graph.V
              .hasLabel[Canonical]
              .toCC[Canonical]
              .headOption
              .getOrElse(throw new IllegalStateException("Can't get canonical"))
          }
        }
      }
    }
}
