package io.mediachain.translation

import java.io.File

import cats.data.Xor
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import io.mediachain._
import io.mediachain.Types._
import io.mediachain.Traversals.GremlinScalaImplicits
import org.json4s._
import org.specs2.Specification
import gremlin.scala._

import scala.io.Source
object FSLoaderSpec extends Specification with XorMatchers {

  object NoopTranslator extends Translator {
    val name = "noop"
    val version = 0
    def translate(json: JObject): Xor[TranslationError, PhotoBlob] = {
      Xor.right(PhotoBlob(None, "test title", "test desc", "test date", None))
    }
  }
  class NoopLoader(val path: String, implicit val translator: NoopTranslator.type = NoopTranslator) extends DirectoryWalkerLoader[NoopTranslator.type]

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
    s2"""
       $ingestsDirectory - Traverses a directory
    """

  def ingestsDirectory = {
    val loader = new NoopLoader(SpecResources.Tate.fixtureDir.getPath)

    val results = loader.pairI.toList

    results must contain(
      beRightXor { pair: (JObject, String) =>
        pair._2 must startWith("{")
      }
    ).forall
    results must have size(be_>(1))
  }
}
