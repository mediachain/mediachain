package org.mediachain.translation

import org.specs2.Specification
import tate.{TateTranslator => SUT}
import cats.data.Xor
import org.mediachain.Types._

object TateTranslatorSpec extends Specification {


  def is = skipAllIf(!SpecResources.Tate.sampleDataExists) ^
  s2"""
       $loadsArtwork - Translates Tate artwork json into PhotoBlob + List[Person]
       $loadsArtworksFromDir - Translates all artworks from a directory structure
    """

  def loadsArtwork = {
    val expected = SpecResources.Tate.SampleArtworkA00001
    val jsonResult = JsonLoader.loadObjectFromFile(expected.jsonFile)
    val translated = jsonResult.flatMap(SUT.loadArtwork)
      .getOrElse(throw new Exception("Unable to translate sample artwork from tate collection"))

    val blob = translated

    (blob.title must_== expected.title) and
      (blob.date must_== expected.dateText) and
      (blob.author must beSome[Person].which(_.name == expected.artistName))
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
