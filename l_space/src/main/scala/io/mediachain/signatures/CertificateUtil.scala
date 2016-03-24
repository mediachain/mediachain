package io.mediachain.signatures

import java.security.cert.X509Certificate
import javax.security.auth.x500.X500Principal

object CertificateUtil {

  def canonicalName(cert: X509Certificate): String =
    cert.getSubjectX500Principal.getName(X500Principal.CANONICAL)

}
