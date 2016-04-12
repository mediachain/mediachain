package io.mediachain.translation

import cats.data.Xor
import io.mediachain._
import io.mediachain.Types._
import org.json4s._
import org.specs2.Specification
import org.specs2.matcher.JsonMatchers
import io.mediachain.core.TranslationError

object FSLoaderSpec extends Specification with XorMatchers with JsonMatchers {

  object NoopTranslator extends Translator {
    val name = "noop"
    val version = 0
    def translate(json: JObject): Xor[TranslationError, BlobBundle] = {
      Xor.right(BlobBundle(ImageBlob(None, "test title", "test desc", "test date")))
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

    results must have size(be_>(999))
    results must contain(
      beRightXor { pair: (JObject, String) =>
        pair._1 must beAnInstanceOf[JObject]
        pair._2 must startWith("{")
      }
    ).forall
  }
}
