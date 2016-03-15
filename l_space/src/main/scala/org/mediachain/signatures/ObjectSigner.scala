package org.mediachain.signatures

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

import cats.data.Xor
import com.jsuereth.pgp._
import org.json4s.JValue
import org.mediachain.MediachainError
import org.mediachain.io.{CborSerializer, JsonParser}
import org.mediachain.signatures.SigningError._


class ObjectSigner(signingKey: SecretKey) {
  PGP.init


  def signText(text: String, passphrase: Array[Char]): Xor[SigningError, String] = {
    catchIncorrectPassphrase {
      signingKey.signString(text, passphrase)
    }
  }

  // TODO: these names are kind of unwieldy... find better ones?

  def signCborRepresentationOfJsonValue(json: JValue, passphrase: Array[Char])
  : Xor[SigningError, String] = {
    val canonicalCbor = CborSerializer.bytesForJsonValue(json)
    catchIncorrectPassphrase {
      val input = new ByteArrayInputStream(canonicalCbor)
      val output = new ByteArrayOutputStream

      signingKey.signStream(input, output, passphrase)
      output.toString("UTF-8")
    }
  }


  def signCborRepresentationOfJsonText(jsonString: String, passphrase: Array[Char])
  :Xor[MediachainError, String] = {
    for {
      parsed <- JsonParser.parseJsonString(jsonString).leftMap(MediachainError.Parsing)
      signature <- signCborRepresentationOfJsonValue(parsed, passphrase)
        .leftMap(MediachainError.Signature)
    } yield signature
  }
}
