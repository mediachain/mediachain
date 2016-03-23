package io.mediachain.signatures

import java.io.{FileInputStream, IOError, InputStream, InputStreamReader}
import java.security.cert.{CertificateFactory, X509Certificate}
import java.security.spec.PKCS8EncodedKeySpec
import java.security.{KeyFactory, PrivateKey}

import cats.data.Xor
import io.mediachain.core.SignatureError
import io.mediachain.core.SignatureError.{InvalidCertificate, PEMIOError}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader

object PEMFileUtil {
  val keyFactory: KeyFactory =
    KeyFactory.getInstance("RSA", new BouncyCastleProvider)

  val certFactory: CertificateFactory =
    CertificateFactory.getInstance("X.509", new BouncyCastleProvider)

  def certificateFromInputStream(inStream: InputStream)
  : Xor[SignatureError, X509Certificate] = {
    Xor.catchNonFatal {
      certFactory.generateCertificate(inStream)
        .asInstanceOf[X509Certificate]
    }.leftMap(PEMIOError)
      .flatMap {
        case null => Xor.left(InvalidCertificate(
          new RuntimeException(
            s"Unable to create X509 certificate")))
        case valid: X509Certificate => Xor.right(valid)
      }
  }


  def privateKeyFromInputStream(inStream: InputStream)
  : Xor[SignatureError, PrivateKey] = {
    Xor.catchNonFatal {
      val reader = new PemReader(new InputStreamReader(inStream))
      val pem = reader.readPemObject()
      reader.close()

      val keySpec = new PKCS8EncodedKeySpec(pem.getContent)
      keyFactory.generatePrivate(keySpec)
    }.leftMap(PEMIOError)
  }


  def certificateFromFile(path: String): Xor[SignatureError, X509Certificate] = {
    Xor.catchOnly[IOError](new FileInputStream(path))
      .leftMap(PEMIOError)
      .flatMap(certificateFromInputStream)
  }


  def privateKeyFromFile(path: String): Xor[SignatureError, PrivateKey] = {
    Xor.catchOnly[IOError](new FileInputStream(path))
      .leftMap(PEMIOError)
      .flatMap(privateKeyFromInputStream)
  }
}
