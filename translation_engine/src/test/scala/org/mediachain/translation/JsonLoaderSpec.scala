package org.mediachain.translation

import org.specs2.Specification

object JsonLoaderSpec extends Specification {

  import java.io.File

  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
        Loads all json objects in a directory structure: $loadsFromDir
      """


  val simpleTestURL = this.getClass.getResource("/simple-test.json")
  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(simpleTestURL)

    json.toEither must beRight
  }


  val tateSampleDir = new File(this.getClass.getResource("/tate-sample").toURI)
  def loadsFromDir = pending
}
