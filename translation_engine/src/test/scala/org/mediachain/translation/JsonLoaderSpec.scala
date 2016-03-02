package org.mediachain.translation

import org.specs2.Specification

object JsonLoaderSpec extends Specification {

  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
      """


  val simpleTestURL = this.getClass.getResource("/simple-test.json")

  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(simpleTestURL)

    json.toEither must beRight
  }
}
