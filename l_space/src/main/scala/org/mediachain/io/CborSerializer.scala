package org.mediachain.io

import cats.data.Xor


object CborSerializer {
  import org.json4s.JValue
  import org.mediachain.io.{CborMethods => Cbor}

  def bytesForJsonValue(json: JValue): Array[Byte] =
    Cbor.bytes(Cbor.render(json))

  def sha1StringForJsonValue(json: JValue): String =
    Cbor.sha1String(Cbor.render(json))

  def bytesForJsonText(jsonString: String): Xor[ParsingError, Array[Byte]] =
    JsonParser.parseJsonString(jsonString)
      .map(bytesForJsonValue)

  def sha1StringForJsonText(jsonString: String): Xor[ParsingError, String] =
    JsonParser.parseJsonString(jsonString)
      .map(sha1StringForJsonValue)
}
