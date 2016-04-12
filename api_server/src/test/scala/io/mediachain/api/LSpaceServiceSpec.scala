package io.mediachain.api

import io.mediachain.{BaseSpec, GraphFixture}
import io.mediachain.Types._
import org.specs2.matcher.{JsonMatchers, JsonType, Matcher}
import spray.testkit.Specs2RouteTest
import gremlin.scala._
import io.mediachain.util.orient.MigrationHelper

object LSpaceServiceSpec extends BaseSpec
  with Specs2RouteTest with LSpaceService with JsonMatchers {
  def actorRefFactory = system

  val graphFactory = MigrationHelper.newInMemoryGraphFactory()
  val context = GraphFixture.Context(graphFactory.getTx)

  def is =
    s2"""
       returns a canonical from GET "/" $returnsFirstCanonical
       returns a canonical by ID $returnsACanonical
       returns a canonical's rev history by ID $returnsASubtree
      """

  def returnsFirstCanonical = {
    Get("/canonicals") ~> baseRoute ~> check {
      responseAs[String] must /#(0) /("canonicalID" -> context.objects.imageBlobCanonical.canonicalID)
    }
  }

  def returnsACanonical = {
    val canonicalId = context.objects.imageBlobCanonical.canonicalID
    Get("/canonicals/" + canonicalId) ~> baseRoute ~> check {
      responseAs[String] must /("canonicalID" -> canonicalId)
    }
  }

  private def aRevisionWith(blob: ImageBlob): Matcher[String] = {
    /("title").andHave(blob.title) and
    /("description").andHave(blob.description) and
    /("date").andHave(blob.date)
    // TODO: how to handle external_ids, signatures?
  }

  private def haveRevisions(revisions: Matcher[String]*): Matcher[String] =
    /("revisions").andHave(exactly(revisions:_*))

  def returnsASubtree = {
    val canonicalId = context.objects.imageBlobCanonical.canonicalID
    Get("/canonicals/" + canonicalId + "/history") ~> baseRoute ~> check {
      val r = responseAs[String]
      r aka "canonical ID" must /("canonicalID" -> canonicalId)
      r aka "revisions list" must haveRevisions(
        List(context.objects.imageBlob, context.objects.modifiedImageBlob).map(aRevisionWith):_*)
    }
  }

}
