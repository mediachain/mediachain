package io.mediachain.api

import akka.actor.ActorSystem
import akka.io.IO
import akka.pattern.ask
import akka.util.Timeout
import io.mediachain.util.Env
import io.mediachain.util.orient.MigrationHelper
import spray.can.Http

import scala.concurrent.duration._
import scala.util.{Failure, Success}

object Boot extends App {
  implicit val system = ActorSystem("l-space-api")

  lazy val graphFactory = MigrationHelper.getMigratedGraphFactory()
    match {
      case Failure(e) =>
        throw new RuntimeException("Unable to connect to orientdb", e)
      case Success(factory) => factory
    }

  lazy val service = system.actorOf(
    HelloServiceActor.props(graphFactory), "hello-service")

  implicit val timeout = Timeout(5.seconds)

  val interface = Env.getString("LSPACE_API_LISTEN_INTERFACE")
    .getOrElse("localhost")

  val port = Env.getInt("LSPACE_API_PORT").getOrElse(8888)

  IO(Http) ? Http.Bind(service, interface = interface, port = port)
}
