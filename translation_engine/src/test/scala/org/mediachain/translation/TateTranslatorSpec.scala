package org.mediachain.translation

import org.mediachain.Types._
import org.mediachain.XorMatchers
import org.specs2.Specification
import tate.{TateTranslator => SUT}

import scala.io.Source

object TateTranslatorSpec extends Specification with XorMatchers {

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
  s2"""
       $loadsArtwork - Translates Tate artwork json into PhotoBlob
       $loadsArtworksFromDir - Translates all artworks from a directory structure
    """

  def loadsArtwork = {
    val expected = SpecResources.Tate.SampleArtworkA00001

    val source = Source.fromFile(expected.jsonFile).mkString
    val context = SUT.TateArtworkContext("testing")

    val translated = context.translate(source)

    translated must beRightXor { result: (PhotoBlob, RawMetadataBlob) =>
      val (blob, raw) = result
      blob.title == expected.title &&
      blob.author.exists(_.name == expected.artistName) &&
      raw.blob == source
    }
  }

  def loadsArtworksFromDir = {
    val jsonResults = JsonLoader.loadObjectsFromDirectoryTree(SpecResources.Tate.fixtureDir)

    val translated = jsonResults.map { result =>
      for {
        obj <- result
        t <- SUT.loadArtwork(obj)
      } yield t
    }

    (jsonResults.size must be_>(0)) and
    (jsonResults.size must_== translated.size)
  }
}
