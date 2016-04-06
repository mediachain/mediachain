package io.mediachain.translation

import java.io.File

import cats.data.Xor
import gremlin.scala._
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import io.mediachain._
import io.mediachain.Types._
import io.mediachain.translation.tate.TateLoader
import org.specs2.Specification
import io.mediachain.core.TranslationError
import io.mediachain.signatures.{LocalCertificateStore, PEMFileUtil, Signatory, Signer}
import org.specs2.execute.Result

object TateIngestionSpec extends BaseSpec with Orientable {

  def is = skipAllUnless(SpecResources.Tate.sampleDataExists) ^
    s2"""
       $ingestsDirectory - Ingests a directory of tate artwork json
    """


  val commonName = "testing.lspace.tate.org.uk"

  lazy val privateKeyStream = getClass.getResourceAsStream(
    "/private-keys/testing.lspace.tate.org.uk/private-key.pem")

  lazy val privateKey = PEMFileUtil.privateKeyFromInputStream(privateKeyStream)
    .getOrElse(throw new IllegalStateException(
      "Can't read testing private key for Tate gallery"))

  lazy val signatory = Signatory(commonName, privateKey)

  lazy val certificateDir = getClass.getResource("/certificates").toURI.getPath

  lazy val certificateStore = new LocalCertificateStore(certificateDir)


  def ingestsDirectory = { graph: OrientGraph =>
    val loader = new TateLoader(SpecResources.Tate.fixtureDir.getPath)
    val translated = loader.loadBlobs(Some(signatory))

    val canonicals = translated.map {
      resultXor: Xor[TranslationError, (BlobBundle, RawMetadataBlob)] =>
        resultXor.flatMap { result: (BlobBundle, RawMetadataBlob) =>
          Ingress.ingestBlobBundle(graph, result._1, Some(result._2))
        }
    }.toVector

    canonicals must have size be_>(1)

    canonicals must contain (beRightXor { canonical: Canonical =>
      canonical.id must beSome
    }).forall

    allBlobsHaveValidSignatures(graph)
  }


  def allBlobsHaveValidSignatures(graph: Graph): Result = {
    val signables  = graph.V.map{ v: Vertex =>
      val signableOpt: Option[Signable] = v.label match {
        case "Person" => Some(v.toCC[Person])
        case "ImageBlob" => Some(v.toCC[ImageBlob])
        case "RawMetadataBlob" => Some(v.toCC[RawMetadataBlob])
        case _ => None
      }
      signableOpt
    }.toList.flatten

    val hasSignatures = signables.map(_.signatures.get(commonName).isDefined)

    val validationResults = signables.map(
      Signer.validateSignableWithCertificateStore(_, certificateStore)
    )

    hasSignatures must contain (beTrue).forall

    validationResults must contain (beRightXor { valid: Boolean =>
      valid must beTrue
    }).forall
  }

}
