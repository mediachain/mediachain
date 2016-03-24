package io.mediachain.signatures

import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x500.style.{BCStyle, IETFUtils}

object CertificateUtil {

  def canonicalName(cert: X509Certificate): String =
    cert.getSubjectX500Principal.getName(X500Principal.CANONICAL)


  def commonNames(cert: X509Certificate): List[String] = {
    val name = new X500Name(cert.getSubjectX500Principal.getName)
    val rdns = name.getRDNs(BCStyle.CN)
    rdns.map(IETFUtils.valueToString).toList
  }
}
