package io.mediachain.translation

import java.io.File

import cats.data.Xor
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import io.mediachain._
import io.mediachain.Types._
import io.mediachain.Traversals.GremlinScalaImplicits
import io.mediachain.translation.tate.TateTranslator.TateArtworkContext
import org.specs2.Specification
import gremlin.scala._

import scala.io.Source

object TateIngestionSpec extends Specification with Orientable with XorMatchers {

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
    s2"""
       $ingestsSingleArtworkWithAuthor - Ingests a single artwork with author
       $ingestsDirectory - Ingests a directory of tate artwork json
    """

  def ingestsSingleArtworkWithAuthor = { graph: OrientGraph =>
    val context = TateArtworkContext("ingestion test")
    val expected = SpecResources.Tate.SampleArtworkA00001
    val contents = Source.fromFile(expected.jsonFile).mkString
    val translated = context.translate(contents)

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

  def ingestsDirectory = { graph: OrientGraph =>
    val context = TateArtworkContext("directory ingestion test")
    val files = DirectoryWalker.findWithExtension(SpecResources.Tate.fixtureDir, ".json")
    val jsonStrings = files.map(Source.fromFile(_).mkString)
    val translated: Iterable[Xor[TranslationError, (PhotoBlob, RawMetadataBlob)]] =
      jsonStrings.map(context.translate)

    val canonicals = translated.map {
      resultXor: Xor[TranslationError, (PhotoBlob, RawMetadataBlob)] =>
        resultXor.flatMap { result: (PhotoBlob, RawMetadataBlob) =>
          Ingress.addPhotoBlob(graph, result._1, Some(result._2))
        }
    }.toVector

    (canonicals must have size(be_>(1))) and
      (canonicals must contain (beRightXor { canonical: Canonical =>
      canonical.id must beSome
    }).forall)
  }
}
