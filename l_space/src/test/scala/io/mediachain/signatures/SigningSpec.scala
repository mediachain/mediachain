package io.mediachain.signatures

import java.security.KeyPairGenerator

import cats.data.Xor
import io.mediachain.Types.Canonical
import io.mediachain.core.MediachainError
import io.mediachain.XorMatchers
import io.mediachain.core.TranslationError.InvalidFormat
import org.specs2.Specification

object SigningSpec extends Specification
  with XorMatchers  {

  def is =
  s2"""
  - Signs a text string $signsText
  - Does not validate signature for modified text $doesNotValidateModifiedText
  - Signs a String containing json text, first converting to canonical CBOR $signsJsonText
  - Signs a Canonical case class $signsCanonical
      """

  val passphrase = "I'll never tell!".toCharArray

  lazy val keygen = KeyPairGenerator.getInstance("RSA")
  lazy val keypair = keygen.generateKeyPair()
  lazy val publicKey = keypair.getPublic
  lazy val privateKey = keypair.getPrivate


  def signsText = {
    val text = "Sign me, please!"
    val signature = Signer.signText(text, privateKey)

    val valid = Signer.verifySignedText(text, signature, publicKey)
    valid must beTrue
  }


  def doesNotValidateModifiedText = {
    val text = "Sign me, please!"
    val signature = Signer.signText(text, privateKey)
    val modifiedText = text + " Freemasons run the country!"

    val valid = Signer.verifySignedText(modifiedText, signature, publicKey)
    valid must beFalse
  }


  def signsJsonText = {
    val jsonString = """  {"title": "Harder Better Faster Stronger", "artist": "Daft Punk"} """
    val reorderedJsonString =
      """{
        | "artist": "Daft Punk",
        | "title": "Harder Better Faster Stronger"
        |}""".stripMargin

    val results: Xor[InvalidFormat, (Boolean, Boolean)] =
      for {
        signature <- Signer.signCborRepresentationOfJsonText(jsonString, privateKey)

        originalIsValid <- Signer.verifySignedJsonText(jsonString, signature, publicKey)

        reorderedIsValid <- Signer.verifySignedJsonText(reorderedJsonString, signature, publicKey)
      } yield (originalIsValid, reorderedIsValid)

    results must beRightXor { results =>
      (results._1 must beTrue) and (results._2 must beTrue)
    }
  }

  def signsCanonical = {
    val c = Canonical.create().withSignature("Test Lord", privateKey)
    val sig = c.signatures("Test Lord")

    Signer.verifySignedSignable(c, sig, publicKey) must beTrue
  }
}
