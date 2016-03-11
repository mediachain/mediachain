package org.mediachain.translation

import org.json4s._
import org.json4s.jackson.JsonMethods._
import com.fasterxml.jackson.core.{JsonParser, JsonFactory}
import com.fasterxml.jackson.core.JsonToken
import java.io.File
import cats.data.Streaming
import cats.implicits._
import org.mediachain.Types.{Person, PhotoBlob}

object MomaLoader {
  def loadPhotoBlob(parser: JsonParser): PhotoBlob = {
    var data = Map[String, String]()

    assert(parser.getCurrentToken == JsonToken.START_OBJECT)

    while (parser.nextToken != JsonToken.END_OBJECT) {
      parser.getCurrentToken match {
        case JsonToken.FIELD_NAME =>
          val name = parser.getCurrentName
          parser.nextToken
          name match {
            case "Title" | "Medium" | "Date" | "Artist" =>
              data = data + (name -> parser.getValueAsString)
            case _ => ()
          }
        case _ => ()
      }
    }

    PhotoBlob(
      None,
      data.getOrElse("Title", ""),
      data.getOrElse("Medium", ""),
      data.getOrElse("Date", ""),
      Some(Person(None, data.getOrElse("Artist", "")))
    )
  }

  def loadBlobList[T](filename: String, objParser: (JsonParser => T)):
  Streaming[T] = {
    val jf = new JsonFactory()
    val parser = jf.createParser(new File(filename))

    assert(parser.nextToken() == JsonToken.START_ARRAY)

    def getObject: Option[T] = {
      if (parser.nextToken == JsonToken.END_ARRAY) {
        None
      } else {
        Some(objParser(parser))
      }
    }

    def getObjectStream: Streaming[T] = {
      getObject match {
        case Some(obj) => obj %:: getObjectStream
        case None      => Streaming.empty[T]
      }
    }

    getObjectStream
  }
}
