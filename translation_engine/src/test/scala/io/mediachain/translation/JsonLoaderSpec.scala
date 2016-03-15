package io.mediachain.translation

import org.specs2.Specification
import org.json4s.JObject


object JsonLoaderSpec extends Specification {

  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
      """

  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(SpecResources.simpleTestResourceUrl)
    json.toEither must beRight
  }
}
