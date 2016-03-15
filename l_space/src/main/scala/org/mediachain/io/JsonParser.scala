package org.mediachain.io

import java.io.IOException

import org.json4s.Extraction
import org.json4s.JsonAST.{JObject, JField}
import org.mediachain.Types.Hashable
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

  def jsonObjectForHashable[H <: Hashable](h: H): JObject = {
    val asJValue = Extraction.decompose(h)
    val filtered = asJValue.filterField {
      case JField(name, _) if h.excludedFields.contains(name) => false
      case _ => true
    }

    JObject(filtered)
  }
}
