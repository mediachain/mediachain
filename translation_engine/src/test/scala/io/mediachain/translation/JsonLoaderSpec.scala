package io.mediachain.translation

import cats.data.Xor
import com.fasterxml.jackson.core.JsonFactory
import io.mediachain.XorMatchers
import org.specs2.Specification
import org.json4s.JObject


object JsonLoaderSpec extends Specification with XorMatchers {

  def is =
    s2"""
        Loads json from a URL into an AST $loadsFromURL
        Parses simple $parsesSimple
        Parses key after object $parsesAfterObject
      """
  val jf = new JsonFactory

  def loadsFromURL = {
    val json = JsonLoader.loadObjectFromURL(SpecResources.simpleTestResourceUrl)
    json.toEither must beRight
  }

  val simple =
    """
      {
        "a": 1
      }
    """.stripMargin
  def parsesSimple = {
    val parser = jf.createParser(simple)
    parser.nextToken()
    val parsed = JsonLoader.parseJOBject(parser)

    parsed must beRightXor
  }

  val afterObject =
    """
  {
    "a": {
      "b": "d"
    },
    "c": "o"
  }
  """.stripMargin

  def parsesAfterObject = {
    val parser = jf.createParser(afterObject)
    parser.nextToken()
    val parsed = JsonLoader.parseJOBject(parser)

    parsed must beRightXor
  }
}
