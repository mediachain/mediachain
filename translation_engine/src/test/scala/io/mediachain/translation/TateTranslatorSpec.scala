package io.mediachain.translation

import io.mediachain.Types._
import io.mediachain.XorMatchers
import org.specs2.Specification
import tate.{TateLoader => SUT}

import scala.io.Source

object TateTranslatorSpec extends Specification with XorMatchers {

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
  s2"""
       $loadsArtwork - Translates Tate artwork json into PhotoBlob
       $loadsArtworksFromDir - Translates all artworks from a directory structure
    """

  def loadsArtwork = {
    val expected = SpecResources.Tate.SampleArtworkA00001

    if (!expected.jsonFile.exists) {
      ok(s"Skipping artwork test for ${expected.jsonFile.getPath}. File does not exist")
    } else {
      val source = Source.fromFile(expected.jsonFile).mkString

      val translated = SUT.translate(source)

      translated must beRightXor { result: (PhotoBlob, RawMetadataBlob) =>
        val (blob, raw) = result
        blob.title == expected.title &&
          blob.author.exists(_.name == expected.artistName) &&
          raw.blob == source
      }
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
