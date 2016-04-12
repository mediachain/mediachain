package io.mediachain.api

import java.util.UUID
import io.mediachain.BaseSpec
import io.mediachain.Types._
import org.specs2.specification.BeforeAll
import org.specs2.matcher.JsonMatchers
import spray.testkit.Specs2RouteTest
import gremlin.scala._
import io.mediachain.util.orient.MigrationHelper

object LSpaceServiceSpec extends BaseSpec
  with Specs2RouteTest with LSpaceService with BeforeAll with JsonMatchers {
  def actorRefFactory = system

  val graphFactory = MigrationHelper.newInMemoryGraphFactory()

  val canonicalId = UUID.randomUUID.toString

  def beforeAll: Unit = {
    val graph = graphFactory.getTx()
    graph + Canonical(None, canonicalId)
  }

  def is =
    s2"""
       returns a canonical from GET "/" $returnsFirstCanonical
       returns a canonical by ID $returnsACanonical
       returns a canonical's rev history by ID $returnsASubtree
      """

  def returnsFirstCanonical = {
    Get("/canonicals") ~> baseRoute ~> check {
      responseAs[String] must /#(0) /("canonicalID" -> canonicalId)
    }
  }

  def returnsACanonical = {
    Get("/canonicals/" + canonicalId) ~> baseRoute ~> check {
      responseAs[String] must /("canonicalID" -> canonicalId)
    }
  }
  def returnsASubtree = pending {
    Get("/canonicals/" + canonicalId + "/history") ~> baseRoute ~> check {
      // TODO: describe tree structure here
      responseAs[String] must /("canonicalID" -> canonicalId)
    }
  }


}
