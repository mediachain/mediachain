package org.mediachain
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain.Types._
import org.specs2.Specification
import gremlin.scala._

object IngressSpec extends Specification with Orientable {

  def is =
    s2"""
        Ingests a PhotoBlob with no Author $ingestsPhoto
        Given a PhotoBlob with an existing Author, doesn't recreate it $findsExistingAuthor
        Given an exact match, don't recreate, only attach RawMetadataBlob $attachesRawMetadata
        Given a new PhotoBlob with a new Author, add new Canonical and new Author $ingestsPhotoBothNew
      """

  def ingestsPhoto = { graph: OrientGraph =>
    val photoBlob = PhotoBlob(None, "A Starry Night", "shiny!", "1/2/2013", None)

    val canonical = Ingress.addPhotoBlob(graph, photoBlob)
    canonical.id must beSome[ElementID]
  }

  def findsExistingAuthor = pending
  def attachesRawMetadata = pending
  def ingestsPhotoBothNew = pending
}
