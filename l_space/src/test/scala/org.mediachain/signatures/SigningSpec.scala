package org.mediachain.signatures

import cats.data.Xor
import com.jsuereth.pgp._
import org.mediachain.{MediachainError, XorMatchers}
import org.specs2.Specification

object SigningSpec extends Specification with XorMatchers {

  def is =
  s2"""
       $signsText - Signs a text string
       $doesNotValidateModifiedText - Does not validate signature for modified text

       $signsJsonText - Signs a String containing json text, first converting to canonical CBOR
    """

  val passphrase = "I'll never tell!".toCharArray

  lazy val (publicKeyRing, secretKeyRing): (PublicKeyRing, SecretKeyRing)
    = PGP.makeNewKeyRings("SigningSpec", passphrase)

  lazy val signer = new ObjectSigner(secretKeyRing.secretKey)
  lazy val checker = new SignatureChecker(secretKeyRing.publicKey)


  def signsText = {
    val text = "Sign me, please!"
    val signatureXor = signer.signText(text, passphrase)

    val valid = for {
      signature <- signatureXor
    } yield checker.verifySignedString(text, signature)

    (signatureXor must beRightXor) and
      (valid must beRightXor { _ must beTrue })
  }


  def doesNotValidateModifiedText = {
    val text = "Sign me, please!"
    val signatureXor = signer.signText(text, passphrase)
    val modifiedText = text + " Freemasons run the country!"

    val valid = for {
      signature <- signatureXor
    } yield checker.verifySignedString(modifiedText, signature)

    (signatureXor must beRightXor) and
      (valid must beRightXor { _ must beFalse })
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
        signature <- signer.signCborRepresentationOfJsonText(jsonString, passphrase)

        originalIsValid <- checker.verifySignedJsonText(jsonString, signature)
          .leftMap(MediachainError.Parsing)

        reorderedIsValid <- checker.verifySignedJsonText(reorderedJsonString, signature)
          .leftMap(MediachainError.Parsing)
      } yield (originalIsValid, reorderedIsValid)

    results must beRightXor { results =>
      (results._1 must beTrue) and (results._2 must beTrue)
    }
  }
}
