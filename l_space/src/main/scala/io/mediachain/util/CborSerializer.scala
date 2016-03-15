package io.mediachain.util

object CborSerializer {
  import cats.data.Xor
  import io.mediachain.Types.Hashable
  import org.json4s.{MappingException, DefaultFormats, Extraction, Formats}
  import ParsingError.{InvalidJson, ConversionToJsonFailed}
  import org.json4s.JValue
  import io.mediachain.util.{CborMethods => Cbor}

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
