package io.mediachain.translation

import io.mediachain.Types._
import io.mediachain.XorMatchers
import org.json4s.JObject
import org.specs2.Specification
import org.specs2.matcher.MatcherMacros
import tate.{TateLoader, TateTranslator}
import org.json4s.jackson.JsonMethods._
// strangely this is needed even though we aren't *defining* any macros per se, for MatcherMacros
import scala.language.experimental.macros

import scala.io.Source

abstract class TranslatorSpec extends Specification with XorMatchers with MatcherMacros {

  val resources: SpecResources.Partner

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
    s2"""
       $loadsArtwork - Translates Tate artwork json into ImageBlob
    """

  def loadsArtwork = {
    val (jsonFile, expected) = resources.sampleArtworks.head

    if (!jsonFile.exists) {
      ok(s"Skipping artwork test for ${jsonFile.getPath}. File does not exist")
    } else {
      val source: String = Source.fromFile(jsonFile).mkString
      val json: JObject = parse(source).asInstanceOf[JObject] // have faith

      val translated = TateTranslator.translate(json)

      translated must beRightXor { blob: ImageBlob =>
        blob must matchA[ImageBlob]
          .title(expected.title)
          .date(expected.date)
          .description(expected.description)
          .author { authorO => authorO must beSome((_: Person) must matchA[Person].name(expected.author.get.name)) }
          .external_ids(_ must havePairs(expected.external_ids.toList:_*))

       // _ must matchA[Person].name(expected.author.name))
//            .external_ids(expected.title)
//        (blob.title must_== expected.title) and
//          (blob.author.exists(_.name must_== expected.artistName) and
//            (blob.external_ids.get("tate:id") must beSome(expected.tateId)))
      }
    }
  }
}

//object TateTranslatorSpec extends Specification with XorMatchers {
//
//  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
//  s2"""
//       $loadsArtwork - Translates Tate artwork json into ImageBlob
//    """
//
//  def loadsArtwork = {
//    val expected = SpecResources.Tate.SampleArtworkA00001
//
//    if (!expected.jsonFile.exists) {
//      ok(s"Skipping artwork test for ${expected.jsonFile.getPath}. File does not exist")
//    } else {
//      val source: String = Source.fromFile(expected.jsonFile).mkString
//      val json: JObject = parse(source).asInstanceOf[JObject] // have faith
//
//      val translated = TateTranslator.translate(json)
//
//      translated must beRightXor { blob: ImageBlob =>
//        (blob.title must_== expected.title) and
//          (blob.author.exists(_.name must_== expected.artistName) and
//            (blob.external_ids.get("tate:id") must beSome(expected.tateId)))
//      }
//    }
//  }
//}

// TODO: MoMA translator spec here too?
