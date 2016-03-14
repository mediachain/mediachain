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
  /** A convenience case class for parsing JSON blobs into a more abstract form.
    *
    * @param Title Title of the work
    * @param Medium Medium of the work (becomes description for now)
    * @param Date Date of the work
    * @param Artist Artist of the work
    */
  case class MomaPhotoBlob(Title: String,
                           Medium: String,
                           Date: String,
                           Artist: String) {

    /** Convenience method that casts a MomaPhotoBlob to a PhotoBlob
      *
      * @return
      */
    def asPhotoBlob: PhotoBlob = PhotoBlob(
      id = None,
      title = Title,
      description = Medium,
      date = Date,
      author = Some(Person(id = None, Artist))
    )
  }

  /** Casts a JValue to a PhotoBlob using `extract` and `asPhotoBlob`
    *
    * @param json The JValue representing a work
    * @return A `PhotoBlob` extracted from the JValue
    */
  def jsonToPhotoBlob(json: JValue): PhotoBlob = {
    implicit val formats = DefaultFormats
    json.extract[MomaPhotoBlob].asPhotoBlob
  }

  /** Assert the presence of a token & advance to the `nextToken` upon success.
    * Upon failure, return an error.
    *
    * @param parser The current parser
    * @param token The token to search for
    * @return Unit or an error
    */
  def parseToken(parser: JsonParser, token: JsonToken): Xor[String, Unit] = {
    val curToken = parser.getCurrentToken
    if (curToken == token) {
      parser.nextToken
      Xor.right({})
    } else {
      Xor.left(s"Invalid token. Expected $token, got $curToken.")
    }
  }

  /** The main workhorse. Parses a JArray, emitting individual objects as they
    * are parsed in a `Streaming`. The objects on the stream are `Xor`'s, to
    * help localize which object a streaming parse fails on, should it fail.
    *
    * In the event of a failure, the parse will stop and the end of the stream
    * will be an Xor value containing the error message.
    *
    * @param parser The current parser
    * @return A `Streaming` of either `JValue`s or `String` error messages
    */
  def parseJArray(parser: JsonParser):
  Streaming[Xor[String, JValue]] = {
    def helper: Streaming[Xor[String, JValue]] = {
      if (parser.getCurrentToken == JsonToken.END_ARRAY) {
        Streaming.empty
      } else {
        parseJValue(parser) match {
          case r@Xor.Right(_) => r %:: helper
          case l => Streaming(l)
        }
      }
    }

    parseToken(parser, JsonToken.START_ARRAY) match {
      case Xor.Right(_)  => helper
      case Xor.Left(msg) => Streaming(Xor.left(msg))
    }
  }

  /** Parses an array of objects strictly, rather than lazily. This is used for
    * parsing arrays within the objects held by the main JArray we are parsing.
    *
    * @param parser The current parser
    * @return Either a `JArray` or an error message
    */
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

  /** Parses a single value, reflecting off of the current token in the parse.
    *
    * @param parser The current parser
    * @return The `JValue` under the parse cursor, or an error if the parse is
    *         not currently at a JValue.
    */
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

  /** Parses a tuple (fieldName: String, fieldValue: JValue)
    *
    * @param parser The current parser
    * @return A `JField` or an error message
    */
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

  /** Strictly parse a list of `JField`s.
    *
    * @param parser The current parser
    * @return A list of `JField`s or an error message
    */
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

  /** Parse a `JObject`
    *
    * @param parser The current parser
    * @return A `JObject` or an error message
    */
  def parseJOBject(parser: JsonParser): Xor[String, JObject] = {
    for {
      _      <- parseToken(parser, JsonToken.START_OBJECT)
      fields <- parseFields(parser)
      _      <- parseToken(parser, JsonToken.END_OBJECT)
    } yield JObject(fields)
  }

  /** Given a filename representing a MoMA-schema list of artworks, parse the
    * file into a stream of `PhotoBlob`s.
    *
    * @param filename The filename to read and parse
    * @return A stream of `PhotoBlob`s
    */
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

