package io.mediachain.signatures

import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

import cats.data.Xor
import io.mediachain.core.SignatureError.CertificateLacksCommonName
import org.bouncycastle.asn1._
import org.bouncycastle.asn1.x500.{RDN, X500Name}
import org.bouncycastle.asn1.x500.style.{BCStyle, IETFUtils}

object CertificateUtil {

  /**
    * Try to extract just the "value" portion of the given `ASN1Object`
    * Used to get a human readable "Common Name" out of an RDN object.
    * The RDN's primitive representation is basically a Set of Vectors
    * of two elements, the first being the "oid" of the field name, and
    * the second being the value as a string.  If we just call the
    * seemingly helpful `IETFUtils.valueToString` method, it will encode
    * the whole thing, e.g. `[["0.1.2.3", "foo.bar.com"]]`
    *
    */
  private def decode(rdn: RDN): String = {
    val maybeDecoded: Option[String] =
      try {
        val set = rdn.toASN1Primitive.asInstanceOf[DERSet]
        val seq = set.getObjectAt(0).asInstanceOf[DERSequence]
        val str = seq.getObjectAt(1).asInstanceOf[DERUTF8String]
        Some(str.getString)
      } catch {
        case _: Throwable => None
      }
    // fallback to `valueToString`, if things fail
    maybeDecoded.getOrElse(IETFUtils.valueToString(rdn))
  }


  def canonicalName(cert: X509Certificate): String =
    cert.getSubjectX500Principal.getName(X500Principal.CANONICAL)


  def commonNames(cert: X509Certificate): List[String] = {
    val name = new X500Name(cert.getSubjectX500Principal.getName)
    val rdns = name.getRDNs(BCStyle.CN)
    rdns.map(decode).toList
  }


  def commonName(cert: X509Certificate)
  : Xor[CertificateLacksCommonName, String] =
    Xor.fromOption(commonNames(cert).headOption, CertificateLacksCommonName())

}
