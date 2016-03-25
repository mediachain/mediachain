package io.mediachain.signatures

import java.security.cert.X509Certificate

import cats.data.Xor
import io.mediachain.core.SignatureError

trait CertificateStore {

  def certificateForCommonName(cn: String)
  : Xor[SignatureError, X509Certificate]


}
