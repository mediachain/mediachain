package org.mediachain.translation

import org.specs2.Specification
import tate.{TateTranslator => SUT}
import cats.data.Xor
import org.mediachain.Types._

object TateTranslatorSpec extends Specification {


  def is =
  s2"""
       $loadsArtwork - Translates Tate artwork json into PhotoBlob + List[Person]
       $loadsArtworksFromDir - Translates all artworks from a directory structure
    """

  def loadsArtwork = {
    val expected = SpecResources.TateSampleArtworkA00001
    val jsonResult = JsonLoader.loadObjectFromURL(expected.url)
    val translated = jsonResult.flatMap(SUT.loadArtwork)
      .getOrElse(throw new Exception("Unable to translate sample artwork from tate collection"))

    val (blob, people) = translated

    (blob.title must_== expected.title) and
      (blob.date must_== expected.dateText) and
      (blob.author must beSome[Person].which(_.name == expected.artistName)) and
      (blob.author must_== people.headOption)
  }

  def loadsArtworksFromDir = {

    val tateSampleDirURI = SpecResources.tateSampleDirResourceUrl.toURI
    val jsonResults = JsonLoader.loadObjectsFromDirectoryTree(tateSampleDirURI)

    val translated = jsonResults.map { result =>
      for {
        obj <- result
        t <- SUT.loadArtwork(obj)
      } yield t
    }

    jsonResults.size must_== translated.size
  }
}
