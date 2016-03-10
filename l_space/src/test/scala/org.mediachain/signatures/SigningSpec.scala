package org.mediachain.signatures

import com.jsuereth.pgp._
import org.mediachain.XorMatchers
import org.specs2.Specification
import org.specs2.specification.BeforeAll

object SigningSpec extends Specification with BeforeAll with XorMatchers {

  def is =
  s2"""
       $signsText - Signs a text string
    """

  def beforeAll = {
    PGP.init
  }

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
}
