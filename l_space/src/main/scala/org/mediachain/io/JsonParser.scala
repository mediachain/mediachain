package org.mediachain.io

import java.io.IOException

import org.mediachain.io.ParsingError.InvalidJson


object JsonParser {
  import cats.data.Xor
  import org.json4s.{DefaultFormats, JValue}
  import org.json4s.jackson.{JsonMethods => Json}
  import com.fasterxml.jackson.core.JsonProcessingException

  implicit val formats = DefaultFormats

  def parseJsonString(jsonString: String): Xor[ParsingError, JValue] = {
    try {
      Xor.right(Json.parse(jsonString))
    } catch {
      case e @ (_ : IOException | _ : JsonProcessingException) =>
        Xor.left(InvalidJson(e.getMessage))
    }
  }
}
