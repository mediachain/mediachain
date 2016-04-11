package io.mediachain.api

import io.mediachain.BaseSpec
import io.mediachain.Types.Canonical
import org.specs2.specification.BeforeAll
import spray.testkit.Specs2RouteTest
import gremlin.scala._
import io.mediachain.util.orient.MigrationHelper

object LSpaceServiceSpec extends BaseSpec
  with Specs2RouteTest with LSpaceService with BeforeAll {
  def actorRefFactory = system

  val graphFactory = MigrationHelper.newInMemoryGraphFactory()

  def beforeAll: Unit = {
    val graph = graphFactory.getTx()
    graph + Canonical.create
  }

  def is =
    s2"""
       returns a canonical from GET "/" $returnsFirstCanonical
      """


  def returnsFirstCanonical = {
    Get("/") ~> helloRoute ~> check {
      responseAs[String] must contain("canonicalID")
    }
  }

}
