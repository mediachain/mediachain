package io.mediachain.signatures
import java.io.File
import java.nio.file.Paths
import java.security.cert.X509Certificate

import cats.data.Xor
import io.mediachain.core.SignatureError
import io.mediachain.core.SignatureError.{CertificateNotFound, CommonNameMismatch, InvalidCertificate}
import io.mediachain.util.Env

/**
  * A `CertificateStore` that loads certs from a local directory.
  * Expects each signatory to have a subdirectory of `rootDirectoryPath`
  * whose name is the Common Name specified in the certificate.
  * In that directory should be an X509 certificate named `certificate.pem`
  *
  * e.g.:
  * $ tree ./certificates
  * .
  * ├── mediachain.mine.nyc
  * │   └── certificate.pem
  * ├── mediachain.moma.org
  * │   └── certificate.pem
  * └── mediachain.tate.org.uk
  *     └── certificate.pem
  *
  * @param rootDirectoryPath absolute path to the directory containing the
  *                          certificates, with the structure described above
  */
class LocalCertificateStore(rootDirectoryPath: String)
  extends CertificateStore {
  private val CERTIFICATE_FILENAME = "certificate.pem"

  val rootDir = new File(rootDirectoryPath)
  if (!(rootDir.exists && rootDir.isDirectory)) {
    throw new IllegalStateException(
      "LocalCertificateStore was initialized with a path to a non-existent "+
      s"directory. Please create $rootDirectoryPath and try again :)"
    )
  }

  /**
    * Load the X509 certificate for the signatory with the given Common Name
    *
    * Will fail if the certificate can't be found, or if the common name in
    * the certificate does not match the one given.
    * @param commonName A string that uniquely identifies the signatory
    *                   e.g. "mediachain.mine.nyc"
    * @return An X509Certificate if successful, otherwise a SignatureError
    */
  def certificateForCommonName(commonName: String)
  : Xor[SignatureError, X509Certificate] = {
    val filePath = Paths.get(rootDirectoryPath, commonName, CERTIFICATE_FILENAME)
    val certFile = filePath.toFile
    for {
      _ <- if (certFile.exists) Xor.Right({})
           else Xor.Left(CertificateNotFound())
      cert <- PEMFileUtil.certificateFromFile(certFile.getAbsolutePath)
      commonNameInCert <- CertificateUtil.commonName(cert)
      _ <- if (commonNameInCert == commonName) Xor.Right({})
           else Xor.Left(CommonNameMismatch(commonName, commonNameInCert))
    } yield cert
  }
}


object LocalCertificateStore {
  private val ROOT_DIR_ENV_VAR = "LSPACE_CERTIFICATE_DIR"

  def getInstance(rootDirectoryPath: Option[String] = None): LocalCertificateStore = {
    val path = rootDirectoryPath
      .orElse(Env.getString(ROOT_DIR_ENV_VAR).toOption)
      .getOrElse(throw new IllegalStateException(
        "Can't create LocalCertificateStore without a root directory path\n" +
        "Either pass in Some(directoryPath) or set the " +
        s"$ROOT_DIR_ENV_VAR environment variable."))

    new LocalCertificateStore(path)
  }
}
