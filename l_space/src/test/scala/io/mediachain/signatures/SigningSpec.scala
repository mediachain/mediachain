package io.mediachain.signatures

import java.security.KeyPairGenerator

import cats.data.Xor
import io.mediachain.core.MediachainError
import io.mediachain.XorMatchers
import org.specs2.Specification

object SigningSpec extends Specification
  with XorMatchers  {

  def is =
  s2"""
       $signsText - Signs a text string
       $doesNotValidateModifiedText - Does not validate signature for modified text

       $signsJsonText - Signs a String containing json text, first converting to canonical CBOR
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

    val results: Xor[MediachainError, (Boolean, Boolean)] =
      for {
        signature <- Signer.signCborRepresentationOfJsonText(jsonString, privateKey)

        originalIsValid <- Signer.verifySignedJsonText(jsonString, signature, publicKey)
          .leftMap(MediachainError.Parsing)

        reorderedIsValid <- Signer.verifySignedJsonText(reorderedJsonString, signature, publicKey)
          .leftMap(MediachainError.Parsing)
      } yield (originalIsValid, reorderedIsValid)

    results must beRightXor { results =>
      (results._1 must beTrue) and (results._2 must beTrue)
    }
  }
}
