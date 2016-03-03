package org.mediachain.translation

import org.specs2.Specification
import org.json4s.JObject


object JsonLoaderSpec extends Specification {

  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
        Loads all json objects in a directory structure: $loadsFromDir
      """

  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(SpecResources.simpleTestResourceUrl)
    json.toEither must beRight
  }


  def loadsFromDir = {
    val tateSampleDirURI = SpecResources.tateSampleDirResourceUrl.toURI
    val jsonResults = JsonLoader.loadObjectsFromDirectoryTree(tateSampleDirURI)
    val asEithers = jsonResults.map(_.toEither)

    (asEithers.size must_== SpecResources.numTateSamples) and
      (asEithers must contain(beRight[JObject]))
  }
}
