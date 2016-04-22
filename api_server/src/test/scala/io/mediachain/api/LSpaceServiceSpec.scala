package io.mediachain.api

import io.mediachain.{BaseSpec, GraphFixture}
import io.mediachain.Types._
import org.specs2.matcher.{JsonMatchers, JsonType, Matcher}
import spray.testkit.Specs2RouteTest
import spray.http.StatusCodes._
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
       returns the works by an author $returnsWorks
      """

  def returnsFirstCanonical = {
    Get("/canonicals") ~> baseRoute ~> check {
      status === OK
      responseAs[String] must /#(0) /("canonicalID" -> context.objects.imageBlobCanonical.canonicalID)
    }
  }

  def returnsACanonical = {
    val canonicalId = context.objects.imageBlobCanonical.canonicalID
    Get("/canonicals/" + canonicalId) ~> baseRoute ~> check {
      status === OK
      responseAs[String] must /("canonicalID" -> canonicalId)
    }
    Get("/canonicals/" + canonicalId + "?with_raw=1") ~> baseRoute ~> check {
      status === OK
      val r = responseAs[String]
      println(r)
      r must /("canonicalID" -> canonicalId)
      r must /("raw" -> startWith("{"))
    }
  }

  private def aRevisionWith(blob: ImageBlob): Matcher[String] = {
    /("title").andHave(blob.title) and
    /("description").andHave(blob.description) and
    /("date").andHave(blob.date)
    // TODO: handle external_ids, signatures, ...
  }

  private def haveRevisions(revisions: Matcher[String]*): Matcher[String] =
    /("revisions").andHave(exactly(revisions:_*))

  def returnsASubtree = {
    val canonicalId = context.objects.imageBlobCanonical.canonicalID
    Get("/canonicals/" + canonicalId + "/history") ~> baseRoute ~> check {
      status === OK
      val r = responseAs[String]
      r aka "canonical ID" must /("canonicalID" -> canonicalId)
      r aka "revisions list" must haveRevisions(
        List(context.objects.imageBlob, context.objects.modifiedImageBlob).map(aRevisionWith):_*)
    }
  }


  def returnsWorks = {
    val personCanonicalID = context.objects.personCanonical.canonicalID
    val imageBlobCanonicalID = context.objects.imageBlobCanonical.canonicalID
    val imageByDuplicateAuthorCanonicalID =
      context.objects.imageByDuplicatePersonCanonical.canonicalID

    Get(s"/canonicals/$personCanonicalID/works") ~> baseRoute ~> check {
      val r = responseAs[String]
      r aka "person canonical ID" must /("canonicalID" -> personCanonicalID)
      r aka "works list" must  /("works").andHave(
        allOf(
          /("canonicalID" -> imageBlobCanonicalID),
          /("canonicalID" -> imageByDuplicateAuthorCanonicalID)
        )
      )
    }
  }
}
