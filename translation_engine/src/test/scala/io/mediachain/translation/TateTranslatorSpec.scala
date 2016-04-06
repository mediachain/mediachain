package io.mediachain.translation

import io.mediachain.Types._
import io.mediachain.BlobBundle
import io.mediachain.XorMatchers
import org.json4s.JObject
import org.specs2.Specification
import org.specs2.matcher.MatcherMacros
import moma.MomaTranslator
import tate.TateTranslator
import org.json4s.jackson.JsonMethods._
import org.specs2.execute.Result
// strangely this is needed even though we aren't *defining* any macros per se, for MatcherMacros
import scala.language.experimental.macros

import scala.io.Source

abstract class TranslatorSpec extends Specification with XorMatchers with MatcherMacros {

  val resources: SpecResources.Partner
  val translator: Translator
  val name: String

  def is =
    s2"""
       $loadsArtwork - Translates $name artwork json into ImageBlob
    """

  def loadsArtwork: Result = {
    val (jsonFile, expected) = resources.sampleArtworks.head

    if (!jsonFile.exists) {
      skipped(s"Skipping artwork test for ${jsonFile.getPath}. File does not exist")
    } else {
      val source: String = Source.fromFile(jsonFile).mkString
      val json: JObject = parse(source).asInstanceOf[JObject] // have faith

      val translated = translator.translate(json)

//      def matchExpectedAuthor(author: Option[Person], expectedAuthor: Option[Person]) = {
//        if(expectedAuthor.isEmpty){
//          author must beNone
//        } else {
//          author must beSome {
//            (_: Person) must matchA[Person]
//              .name(expectedAuthor.get.name)
//              .external_ids(_ must havePairs(expectedAuthor.get.external_ids.toList: _*))
//          }
//        }
//      }

      translated must beRightXor { bundle: BlobBundle =>
        bundle.content.asInstanceOf[ImageBlob] must matchA[ImageBlob]
          .title(expected.title)
          .date(expected.date)
          .description(expected.description)
          .external_ids(_ must havePairs(expected.external_ids.toList:_*))
      }
    }
  }
}

object TateTranslatorSpec extends TranslatorSpec {
  val resources = SpecResources.Tate
  val translator = TateTranslator
  val name = "Tate"
}

object MomaTranslatorSpec extends TranslatorSpec {
  val resources = SpecResources.Moma
  val translator = MomaTranslator
  val name = "MoMA"
}

