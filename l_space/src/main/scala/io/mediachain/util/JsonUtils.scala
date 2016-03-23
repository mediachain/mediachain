package io.mediachain.util

import java.io.IOException

import org.json4s.JsonAST.{JField, JObject}
import org.json4s.{Extraction, MappingException}
import io.mediachain.Types.Hashable
import io.mediachain.core.TranslationError.{ConversionToJsonFailed, InvalidFormat}

object JsonUtils {
  import cats.data.Xor
  import com.fasterxml.jackson.core.JsonProcessingException
  import org.json4s.jackson.{JsonMethods => Json}
  import org.json4s.{DefaultFormats, JValue}

  implicit val formats = DefaultFormats

  def parseJsonString(jsonString: String): Xor[InvalidFormat, JValue] = {
    try {
      Xor.right(Json.parse(jsonString))
    } catch {
      case e @ (_ : IOException | _ : JsonProcessingException) =>
        Xor.left(InvalidFormat())
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
