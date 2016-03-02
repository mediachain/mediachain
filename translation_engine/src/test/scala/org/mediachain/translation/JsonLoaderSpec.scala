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

  val simpleTestURL = resourceUrl(simpleTestResourcePath)
  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(simpleTestURL)

    json.toEither must beRight
  }


  def loadsFromDir = {

    val tateSampleDirURI = resourceUrl(tateSampleDirResourcePath).toURI
    val jsonResults = JsonLoader.loadObjectsFromDirectoryTree(tateSampleDirURI)
    val asEitherList = jsonResults.map(_.toEither).toList

    (asEitherList.size must_== numTateSamples) and
      (asEitherList must contain(beRight[JObject]))
  }
}
