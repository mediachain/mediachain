package io.mediachain.translation

import com.fasterxml.jackson.core.JsonFactory

import io.mediachain.core.TranslationError
import io.mediachain.core.TranslationError.{ResourceNotReadable, ParsingFailed}

object JsonLoader {

  import scala.io.Source
  import java.io.File
  import java.net.{URI, URL}
  import org.json4s._
  import org.json4s.jackson.JsonMethods._
  import cats.data.Xor
  import dogs.Streaming
  import com.fasterxml.jackson.core.{JsonParser, JsonToken}

  def createParser(s: String)(implicit factory: JsonFactory): Xor[TranslationError, JsonParser] = {
    val parserXor = Xor.catchNonFatal(factory.createParser(s))
    parserXor.map(parser => parser.nextToken())
    parserXor.leftMap(ParsingFailed)
  }

  def createParser(f: File)(implicit factory: JsonFactory): Xor[TranslationError, JsonParser] = {
    val parserXor = Xor.catchNonFatal(factory.createParser(f))
    parserXor.map(parser => parser.nextToken())
    parserXor.leftMap(ParsingFailed)
  }

  def loadObjectFromString(jsonString: String): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(parse(jsonString).asInstanceOf[JObject])
        .leftMap(ParsingFailed)
  }

  def loadObjectFromSource(source: Source): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(source.mkString)
      .leftMap(ResourceNotReadable)
      .flatMap(loadObjectFromString)
  }

  def loadObjectFromURI(uri: URI): Xor[TranslationError, JObject] =
    loadObjectFromURL(uri.toURL)

  def loadObjectFromURL(url: URL): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(Source.fromURL(url))
      .leftMap(ResourceNotReadable)
      .flatMap(loadObjectFromSource)
  }

  def loadObjectFromFile(file: File): Xor[TranslationError, JObject] = {
    Xor.catchNonFatal(Source.fromFile(file))
      .leftMap(ResourceNotReadable)
      .flatMap(loadObjectFromSource)
  }

  def loadObjectsFromDirectoryTree(directory: File, fileExtension: String = ".json")
  : Iterator[Xor[TranslationError, JObject]] = {
    val files = DirectoryWalker.findWithExtension(directory, fileExtension)
    files.map(loadObjectFromFile)
  }

  /** Assert the presence of a token & advance to the `nextToken` upon success.
    * Upon failure, return an error.
    *
    * @param parser The current parser
    * @param token The token to search for
    * @return Unit or an error
    */
  def parseToken(parser: JsonParser, token: JsonToken, advance: Boolean = true):
  Xor[String, Unit] = {
    val curToken = parser.getCurrentToken
    if (curToken == token) {
      if (advance) {
        Xor.catchNonFatal(parser.nextToken).leftMap(_.getMessage).map(_ => {})
      } else {
        Xor.right({})
      }
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
    * An important note: despite the immutable and sharable nature of the
    * `Streaming` type, this stream can *only be consumed once*. The `Streaming`
    * type was chosen because of its laziness, which is important for helping us
    * avoid memory pressure when parsing large arrays.
    *
    * @param parser The current parser
    * @return A `Streaming` of either `JValue`s or `String` error messages
    */
  def parseJArray(parser: JsonParser):
  Iterator[Xor[String, JValue]] = {
    def helper: Streaming[Xor[String, JValue]] = {
      if (parser.getCurrentToken == JsonToken.END_ARRAY) {
        Streaming.empty
      } else {
        parseJValue(parser) match {
          case r@Xor.Right(_) =>
            parser.nextToken
            Streaming.cons(r, helper)
          case l => Streaming(l)
        }
      }
    }

    (parseToken(parser, JsonToken.START_ARRAY) match {
      case Xor.Right(_)  => helper
      case Xor.Left(msg) => Streaming(Xor.left(msg))
    }).iterator
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
        for {
          result <- parseJValue(parser)
          _ <- Xor.catchNonFatal(parser.nextToken()).leftMap(_.getMessage)
          inner <- helper(result :: results)
        } yield inner
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
    val field = parser.getCurrentName
    for {
      _ <- parseToken(parser, JsonToken.FIELD_NAME)
      value <- parseJValue(parser)
      _ <- Xor.catchNonFatal(parser.nextToken()).leftMap(_.getMessage)
    } yield {
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
      parser.getCurrentToken match {
        case JsonToken.FIELD_NAME =>
          parseField(parser).flatMap(result => helper(result :: results))
        case JsonToken.END_OBJECT =>
          Xor.right(results.reverse)
        case tok =>
          Xor.left(s"Invalid token. Got: $tok")
      }
    }

    helper(Nil)
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
      _      <- parseToken(parser, JsonToken.END_OBJECT, advance = false)
    } yield JObject(fields)
  }
}
