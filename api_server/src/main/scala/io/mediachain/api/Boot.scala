package io.mediachain.api

import akka.actor.{ActorSystem, Props}
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import io.mediachain.util.Env
import spray.can.Http

import scala.concurrent.duration._

object Boot extends App {
  implicit val system = ActorSystem("l-space-api")

  val service = system.actorOf(Props[HelloServiceActor], "hello-service")

  implicit val timeout = Timeout(5.seconds)

  val interface = Env.getString("LSPACE_API_LISTEN_INTERFACE")
    .getOrElse("localhost")

  val port = Env.getInt("LSPACE_API_PORT").getOrElse(8888)

  IO(Http) ? Http.Bind(service, interface = interface, port = port)
}
