package org.mediachain.translation

import org.specs2.Specification
import tate.{TateTranslator => SUT}
import cats.data.Xor
import org.mediachain.Types._

object TateTranslatorSpec extends Specification {


  def is =
  s2"""
       $loadsArtwork - Translates Tate artwork json into PhotoBlob + List[Person]
    """

  def loadsArtwork = {

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
