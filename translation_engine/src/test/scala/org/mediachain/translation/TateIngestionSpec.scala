package org.mediachain.translation

import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain._
import org.mediachain.Types._
import org.mediachain.Traversals.GremlinScalaImplicits
import org.mediachain.translation.tate.TateTranslator.TateArtworkContext
import org.specs2.Specification
import gremlin.scala._

import scala.io.Source

object TateIngestionSpec extends Specification with Orientable with XorMatchers {

  def is =
    s2"""
       $ingestsSingleArtworkWithAuthor - Ingests a single artwork with author
    """

  def ingestsSingleArtworkWithAuthor = { graph: OrientGraph =>
    val loader = TateArtworkContext("ingestion test")
    val expected = SpecResources.Tate.SampleArtworkA00001
    val contents = Source.fromFile(expected.jsonFile).mkString
    val translated = loader.translate(contents)

    val photoCanonical = translated.flatMap { result: (PhotoBlob, RawMetadataBlob) =>
      Ingress.addPhotoBlob(graph, result._1, Some(result._2))
    }

    val queriedAuthor = translated.flatMap { result: (PhotoBlob, RawMetadataBlob) =>
      Traversals.photoBlobsWithExactMatch(graph.V, result._1)
          .findAuthorXor
    }


    (photoCanonical must beRightXor) and
      (queriedAuthor must beRightXor)
  }
}
