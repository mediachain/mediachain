package io.mediachain.api

import io.mediachain.BaseSpec
import spray.testkit.Specs2RouteTest

object HelloServiceSpec extends BaseSpec with Specs2RouteTest with HelloService {
  def actorRefFactory = system


  def is =
    s2"""
       returns a random canonical from GET "/" $returnsRandomCanonical
      """


  def returnsRandomCanonical = {
    Get("/") ~> helloRoute ~> check {
      responseAs[String] must contain("canonicalID")
    }
  }

}
