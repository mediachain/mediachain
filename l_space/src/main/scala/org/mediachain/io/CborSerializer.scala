package org.mediachain.io

import cats.data.Xor
import org.json4s.{MappingException, DefaultFormats, Extraction, Formats}
import org.mediachain.Types.Hashable
import org.mediachain.io.ParsingError.{InvalidJson, ConversionToJsonFailed}


object CborSerializer {
  import org.json4s.JValue
  import org.mediachain.io.{CborMethods => Cbor}

  def bytesForJsonValue(json: JValue): Array[Byte] =
    Cbor.bytes(Cbor.render(json))

  def sha1StringForJsonValue(json: JValue): String =
    Cbor.sha1String(Cbor.render(json))

  def bytesForJsonText(jsonString: String): Xor[InvalidJson, Array[Byte]] =
    JsonParser.parseJsonString(jsonString)
      .map(bytesForJsonValue)

  def sha1StringForJsonText(jsonString: String): Xor[InvalidJson, String] =
    JsonParser.parseJsonString(jsonString)
      .map(sha1StringForJsonValue)

  def bytesForValue[A <: AnyRef](a: A)(implicit formats: Formats = DefaultFormats)
  : Xor[ConversionToJsonFailed, Array[Byte]] = {
    val asJValue = Xor.catchOnly[MappingException] {
      Extraction.decompose(a)(formats)
    }.leftMap(e => ConversionToJsonFailed(e.getMessage))

    asJValue.map(bytesForJsonValue)
  }

  def bytesForHashable[H <: Hashable](h: H): Xor[ConversionToJsonFailed, Array[Byte]] = {
    val jObjectXor = JsonParser.jsonObjectForHashable(h)

    jObjectXor.map(bytesForJsonValue)
  }
}
