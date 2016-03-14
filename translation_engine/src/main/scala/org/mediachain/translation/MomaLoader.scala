package org.mediachain.translation

import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.fasterxml.jackson.core.{JsonParser, JsonFactory}
import com.fasterxml.jackson.core.JsonToken
import java.io.File
import cats.data.{Xor, Streaming}
import cats.implicits._
import org.mediachain.Types.{Person, PhotoBlob}

object MomaLoader {
  case class MomaPhotoBlob(Title: String,
                           Medium: String,
                           Date: String,
                           Artist: String) {
    def asPhotoBlob: PhotoBlob = PhotoBlob(
      id = None,
      title = Title,
      description = Medium,
      date = Date,
      author = Some(Person(id = None, Artist))
    )
  }

  def jsonToPhotoBlob(json: JValue): PhotoBlob = {
    implicit val formats = DefaultFormats
    json.extract[MomaPhotoBlob].asPhotoBlob
  }

  def parseToken(parser: JsonParser, token: JsonToken): Xor[String, Unit] = {
    val curToken = parser.getCurrentToken
    if (curToken == token) {
      parser.nextToken
      Xor.right({})
    } else {
      Xor.left(s"Invalid token. Expected $token, got $curToken.")
    }
  }

  def parseJArray(parser: JsonParser):
  Streaming[Xor[String, JValue]] = {
    def helper: Streaming[Xor[String, JValue]] = {
      if (parser.getCurrentToken == JsonToken.END_ARRAY) {
        Streaming.empty
      } else {
        parseJValue(parser) match {
          case r@Xor.Right(_) => r %:: helper
          case l => l %:: Streaming.empty[Xor[String, JValue]]
        }
      }
    }

    parseToken(parser, JsonToken.START_ARRAY) match {
      case Xor.Right(_)  => helper
      case Xor.Left(msg) => Streaming(Xor.left(msg))
    }
  }

  def parseSubJArray(parser: JsonParser): Xor[String, JArray] = {
    def helper(results: List[JValue]): Xor[String, List[JValue]] = {
      if (parser.getCurrentToken == JsonToken.END_ARRAY) {
        Xor.right(results.reverse)
      } else {
        parseJValue(parser).flatMap(result => helper(result :: results))
      }
    }

    for {
      _ <- parseToken(parser, JsonToken.START_ARRAY)
      objects <- helper(List())
    } yield JArray(objects)
  }

  def parseJValue(parser: JsonParser): Xor[String, JValue] = {
    parser.getCurrentToken match {
      case JsonToken.START_OBJECT => parseJOBject(parser)
      case JsonToken.START_ARRAY => parseSubJArray(parser)
      case JsonToken.VALUE_FALSE | JsonToken.VALUE_TRUE =>
        Xor.right(JBool(parser.getBooleanValue))
      case JsonToken.VALUE_STRING => Xor.right(JString(parser.getText))
      case JsonToken.VALUE_NUMBER_INT => Xor.right(JInt(parser.getIntValue))
      case JsonToken.VALUE_NUMBER_FLOAT =>
        Xor.right(JDouble(parser.getDoubleValue))
      case JsonToken.VALUE_NULL => Xor.right(JNull(0))
      case tok => Xor.left(s"Unrecognized token: $tok")
    }
  }

  def parseField(parser: JsonParser): Xor[String, JField] = {
    for {
      _ <- parseToken(parser, JsonToken.FIELD_NAME)
      field = parser.getCurrentName
      value <- parseJValue(parser)
    } yield {
      parser.nextToken
      (field, value)
    }
  }

  def parseFields(parser: JsonParser): Xor[String, List[JField]] = {
    def helper(results: List[JField]): Xor[String, List[JField]] = {
      if (parser.getCurrentToken == JsonToken.FIELD_NAME) {
        parseField(parser).flatMap(result => helper(result :: results))
      } else {
        Xor.right(results.reverse)
      }
    }

    helper(List())
  }

  def parseJOBject(parser: JsonParser): Xor[String, JObject] = {
    for {
      _      <- parseToken(parser, JsonToken.START_OBJECT)
      fields <- parseFields(parser)
      _      <- parseToken(parser, JsonToken.END_OBJECT)
    } yield JObject(fields)
  }

  def loadPhotoBlobs(filename: String): Streaming[PhotoBlob] = {
    val jf = new JsonFactory
    val parser = jf.createParser(new File(filename))

    parser.nextToken

    parseJArray(parser).flatMap {
      case Xor.Right(json) => Streaming(jsonToPhotoBlob(json))
      case _ => Streaming.empty
    }
  }
}

