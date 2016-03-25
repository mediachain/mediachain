package io.mediachain.translation

import java.io.File

import cats.data.Xor
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import io.mediachain._
import io.mediachain.Types._
import io.mediachain.translation.tate.TateLoader
import org.specs2.Specification
import io.mediachain.core.TranslationError

object TateIngestionSpec extends Specification with Orientable with XorMatchers {

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
    s2"""
       $ingestsDirectory - Ingests a directory of tate artwork json
    """

  def ingestsDirectory = { graph: OrientGraph =>
    val loader = new TateLoader(SpecResources.Tate.fixtureDir.getPath)
    val translated = loader.loadPhotoBlobs()

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
