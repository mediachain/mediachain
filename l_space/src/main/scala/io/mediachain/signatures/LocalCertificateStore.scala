package io.mediachain.signatures
import java.nio.file.Paths
import java.security.cert.X509Certificate

import cats.data.Xor
import io.mediachain.core.SignatureError
import io.mediachain.core.SignatureError.{CertificateNotFound, CommonNameMismatch, InvalidCertificate}

class LocalCertificateStore(rootDirectoryPath: String)
  extends CertificateStore {
  private val CERTIFICATE_FILENAME = "certificate.pem"


  def certificateForCommonName(cn: String)
  : Xor[SignatureError, X509Certificate] = {
    val filePath = Paths.get(rootDirectoryPath, cn, CERTIFICATE_FILENAME)
    val certFile = filePath.toFile
    for {
      _ <- if (certFile.exists) Xor.Right({}) else Xor.Left(CertificateNotFound())
      cert <- PEMFileUtil.certificateFromFile(certFile.getAbsolutePath)
      commonName <- CertificateUtil.commonName(cert)
      _ <- if (commonName == cn) Xor.Right({})
           else Xor.Left(CommonNameMismatch(expectedName = cn, actualName = commonName))
    } yield cert
  }
}
