package io.mediachain.signatures

import java.io.{ByteArrayOutputStream, ByteArrayInputStream}

import cats.data.Xor
import com.jsuereth.pgp._
import io.mediachain.MediachainError
import io.mediachain.signatures.SigningError._
import io.mediachain.Types.Hashable
import io.mediachain.util.{CborSerializer, JsonParser}
import org.json4s.JValue



class ObjectSigner(signingKey: SecretKey) {
  PGP.init


  def signText(text: String, passphrase: Array[Char]): Xor[SigningError, String] = {
    catchIncorrectPassphrase {
      signingKey.signString(text, passphrase)
    }
  }


  def signBytes(bytes: Array[Byte], passphrase: Array[Char]): Xor[SigningError, String] = {
    catchIncorrectPassphrase {
      val input = new ByteArrayInputStream(bytes)
      val output = new ByteArrayOutputStream

      signingKey.signStream(input, output, passphrase)
      output.toString("UTF-8")
    }
  }


  def signatureForHashable[H <: Hashable](h: H, passphrase: Array[Char])
  : Xor[MediachainError, String] = {
    val bytesXor = CborSerializer.bytesForHashable(h)
        .leftMap(MediachainError.Parsing)

    bytesXor.flatMap { bytes =>
      signBytes(bytes, passphrase)
          .leftMap(MediachainError.Signature)
    }
  }

  // TODO: these names are kind of unwieldy... find better ones?
  def signCborRepresentationOfJsonValue(json: JValue, passphrase: Array[Char])
  : Xor[SigningError, String] = {
    val canonicalCbor = CborSerializer.bytesForJsonValue(json)
    signBytes(canonicalCbor, passphrase)
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
