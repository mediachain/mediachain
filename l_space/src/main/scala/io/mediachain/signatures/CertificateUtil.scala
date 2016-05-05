package io.mediachain.signatures

import java.io.IOException
import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

import cats.data.Xor
import io.mediachain.core.SignatureError.CertificateLacksCommonName
import sun.security.x509.X500Name

object CertificateUtil {

  def canonicalName(cert: X509Certificate): String =
    cert.getSubjectX500Principal.getName(X500Principal.CANONICAL)


  def commonName(cert: X509Certificate)
  : Xor[CertificateLacksCommonName, String] =
    Xor.catchOnly[IOException] {
      val name = new X500Name(cert.getSubjectX500Principal.getName)
      name.getCommonName
    }.leftMap(_ => CertificateLacksCommonName())

}
