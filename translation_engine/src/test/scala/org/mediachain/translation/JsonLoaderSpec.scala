package org.mediachain.translation

import org.specs2.Specification
import org.json4s.JObject


object JsonLoaderSpec extends Specification {
  val simpleTestResourcePath = "/simple-test.json"
  val tateSampleDirResourcePath = "/tate-sample"
  val numTateSamples = 18


  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
        Loads all json objects in a directory structure: $loadsFromDir
      """

  def resourceUrl(path: String) = this.getClass.getResource(path)

  def loadsFromURL = {
    val simpleTestURL = resourceUrl(simpleTestResourcePath)
    val json = JsonLoader.loadObjectFromURL(simpleTestURL)

    json.toEither must beRight
  }


  def loadsFromDir = {
    val tateSampleDirURI = resourceUrl(tateSampleDirResourcePath).toURI
    val jsonResults = JsonLoader.loadObjectsFromDirectoryTree(tateSampleDirURI)
    val asEithers = jsonResults.map(_.toEither)

    (asEithers.size must_== numTateSamples) and
      (asEithers must contain(beRight[JObject]))
  }
}
