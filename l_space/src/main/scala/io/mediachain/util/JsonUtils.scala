package io.mediachain.util

import java.io.IOException

import io.mediachain.Types.{Hashable, Signable}
import io.mediachain.core.TranslationError.InvalidFormat
import org.json4s.FieldSerializer.ignore
import org.json4s.JsonAST.JObject
import org.json4s.jackson.{JsonMethods => Json}
import org.json4s.{Extraction, FieldSerializer, MappingException}

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

  def jsonObjectForHashable[H <: Hashable](h: H):  JObject = {
    try {
      val formatsWithSerializer = DefaultFormats + h.hashSerializer
      val asJValue = Extraction.decompose(h)(formatsWithSerializer)
      asJValue.asInstanceOf[JObject]
    } catch {
      case e: MappingException =>
        throw new IllegalStateException(
          "Types extending the Hashable trait must be convertible to " +
            "JSON objects.  Conversion failed: " + e.getMessage, e)
      case e: Throwable => throw e
    }
  }


  def jsonObjectForSignable[S <: Signable](s: S):  JObject = {
    try {
      val formatsWithSerializer = DefaultFormats + s.signingSerializer
      val asJValue = Extraction.decompose(s)(formatsWithSerializer)
      asJValue.asInstanceOf[JObject]
    } catch {
      case e: MappingException =>
        throw new IllegalStateException(
          "Types extending the Signable trait must be convertible to " +
            "JSON objects.  Conversion failed: " + e.getMessage, e)
      case e: Throwable => throw e
    }
  }
}
