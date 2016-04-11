package io.mediachain.api

import akka.actor.Actor
import spray.routing.HttpService
import spray.http._
import io.mediachain.Types.Canonical
import org.json4s.DefaultFormats
import spray.httpx.Json4sJacksonSupport

class HelloServiceActor extends Actor with HelloService {

  def actorRefFactory = context

  def receive = runRoute(helloRoute)
}

object JsonSupport extends Json4sJacksonSupport {
  implicit def json4sJacksonFormats = DefaultFormats
}

trait HelloService extends HttpService {
  import JsonSupport._

  val helloRoute =
    path("") {
      get {
        complete {
          Canonical.create()
        }
      }
    }
}
