package io.mediachain.api

import akka.actor.{Actor, Props}
import spray.routing.HttpService
import spray.http._
import io.mediachain.Types._
import org.json4s.DefaultFormats
import spray.httpx.Json4sJacksonSupport
import gremlin.scala._
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

object HelloServiceActor {
  def props(graphFactory: OrientGraphFactory): Props =
    Props(classOf[HelloServiceActor], graphFactory)
}

class HelloServiceActor(val graphFactory: OrientGraphFactory)
  extends Actor with HelloService {

  def actorRefFactory = context

  def receive = runRoute(helloRoute)
}

object JsonSupport extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats = DefaultFormats
}

trait HelloService extends HttpService {
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
