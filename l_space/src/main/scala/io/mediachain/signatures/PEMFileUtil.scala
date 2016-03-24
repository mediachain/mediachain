package io.mediachain.signatures

import java.io._
import java.security.cert.{CertificateException, CertificateFactory, X509Certificate}
import java.security.spec.{InvalidKeySpecException, PKCS8EncodedKeySpec}
import java.security.{KeyFactory, PrivateKey, Security}

import cats.data.Xor
import io.mediachain.core.SignatureError
import io.mediachain.core.SignatureError.{InvalidCertificate, PEMIOError}
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader

object PEMFileUtil {
  Security.addProvider(new BouncyCastleProvider)
  val CRYPTO_PROVIDER = BouncyCastleProvider.PROVIDER_NAME

  private val keyFactory: KeyFactory =
    KeyFactory.getInstance("RSA", CRYPTO_PROVIDER)

  private val certFactory: CertificateFactory =
    CertificateFactory.getInstance("X.509", CRYPTO_PROVIDER)

  def certificateFromInputStream(inStream: InputStream)
  : Xor[SignatureError, X509Certificate] =
    Xor.catchOnly[CertificateException] {
      val cert =
        certFactory.generateCertificate(inStream).asInstanceOf[X509Certificate]
      if (cert == null) {
        throw new CertificateException("Unable to create X509 certificate")
      }
      cert
    }.leftMap(InvalidCertificate)


  def privateKeyFromInputStream(inStream: InputStream)
  : Xor[SignatureError, PrivateKey] = {
    Xor.catchOnly[IOException] {
      val reader = new PemReader(new InputStreamReader(inStream))
      val pem = reader.readPemObject()
      reader.close()
      new PKCS8EncodedKeySpec(pem.getContent)
    }.leftMap(PEMIOError)
      .flatMap { keySpec =>
        Xor.catchOnly[InvalidKeySpecException] {
          keyFactory.generatePrivate(keySpec)
        }.leftMap(InvalidCertificate)
      }
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
