package org.mediachain.io

import java.io.IOException

import org.json4s.{MappingException, Extraction}
import org.json4s.JsonAST.{JObject, JField}
import org.mediachain.Types.Hashable
import org.mediachain.io.ParsingError.{ConversionToJsonFailed, InvalidJson}


object JsonParser {
  import cats.data.Xor
  import org.json4s.{DefaultFormats, JValue}
  import org.json4s.jackson.{JsonMethods => Json}
  import com.fasterxml.jackson.core.JsonProcessingException

  implicit val formats = DefaultFormats

  def parseJsonString(jsonString: String): Xor[InvalidJson, JValue] = {
    try {
      Xor.right(Json.parse(jsonString))
    } catch {
      case e @ (_ : IOException | _ : JsonProcessingException) =>
        Xor.left(InvalidJson(e.getMessage))
    }
  }

  def jsonObjectForHashable[H <: Hashable](h: H): Xor[ConversionToJsonFailed, JObject] = {
    Xor.catchOnly[MappingException] {
      val asJValue = Extraction.decompose(h)
      val filtered = asJValue.filterField {
        case JField(name, _) if h.excludedFields.contains(name) => false
        case _ => true
      }

      JObject(filtered)
    }
  }.leftMap(e => ConversionToJsonFailed(e.getMessage))
}
