package io.mediachain.translation

import java.io.File
import java.security.PrivateKey

import scala.io.Source
import cats.data.Xor
import io.mediachain.Types.{Canonical, PhotoBlob, RawMetadataBlob}
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}
import org.json4s._
import io.mediachain.core.{Error, TranslationError}
import io.mediachain.Ingress
import io.mediachain.translation.JsonLoader.parseJArray
import org.json4s.jackson.Serialization.write
import com.fasterxml.jackson.core.JsonFactory
import io.mediachain.signatures.PEMFileUtil

trait Implicit {
  implicit val factory = new JsonFactory
}
object `package` extends Implicit

case class Signatory(commonName: String, privateKey: PrivateKey)

trait Translator {
  val name: String
  val version: Int
  def translate(source: JObject): Xor[TranslationError, PhotoBlob]
}

trait FSLoader[T <: Translator] {
  val translator: T

  val pairI: Iterator[Xor[TranslationError, (JObject, String)]]
  val path: String

  def loadPhotoBlobs(signatory: Option[Signatory] = None)
  : Iterator[Xor[TranslationError,(PhotoBlob, RawMetadataBlob)]] = {
    pairI.map { pairXor =>
      pairXor.flatMap { case (json, raw) =>
        translator.translate(json).map { photoBlob: PhotoBlob =>
          val rawBlob = RawMetadataBlob (None, raw)

          signatory match {
            case Some(Signatory(commonName, privateKey)) =>
              (photoBlob.withSignature(commonName, privateKey),
                rawBlob.withSignature(commonName, privateKey))
            case _ =>
              (photoBlob, rawBlob)
          }
        }
      }
    }
  }
}

trait DirectoryWalkerLoader[T <: Translator] extends FSLoader[T] {
  val fileI: Iterator[File] = DirectoryWalker.findWithExtension(new File(path), ".json")

  val (jsonI, rawI) = {
    val (left, right) = fileI.duplicate
    val jsonI = {
      left.map { file =>
        val obj = for {
          parser <- JsonLoader.createParser(file)
          obj <- JsonLoader.parseJOBject(parser)
        } yield obj

        obj.leftMap(err =>
          TranslationError.ParsingFailed(new RuntimeException(err + " at " + file.toString)))
      }
    }
    val rawI = right.map(Source.fromFile(_).mkString)

    (jsonI, rawI)
  }

  val pairI = jsonI.zip(rawI).map {
    case (jsonXor, raw) => jsonXor.map((_,raw))
    case _ => throw new RuntimeException("Should never get here")
  }
}

trait FlatFileLoader[T <: Translator] extends FSLoader[T] {
  val pairI = {
    implicit val formats = org.json4s.DefaultFormats

    JsonLoader.createParser(new File(path)) match {
      case err@Xor.Left(_) => Iterator(err)
      case Xor.Right(parser) => {
        parseJArray(parser).map {
          case Xor.Right(json: JObject) => Xor.right((json, write(json)))
          case err@(Xor.Left(_) | Xor.Right(_)) => Xor.left(TranslationError.ParsingFailed(new RuntimeException(err.toString)))
        }
      }
    }
  }
}

object TranslatorDispatcher {
  // TODO: move + inject me
  def getGraph: OrientGraph = {
    val url = sys.env.getOrElse("ORIENTDB_URL", throw new Exception("ORIENTDB_URL required"))
    val user = sys.env.getOrElse("ORIENTDB_USER", throw new Exception("ORIENTDB_USER required"))
    val password = sys.env.getOrElse("ORIENTDB_PASSWORD", throw new Exception("ORIENTDB_PASSWORD required"))
    val graph = new OrientGraphFactory(url, user, password).getNoTx()

    graph
  }
  def dispatch(partner: String, path: String, signingIdentity: String, privateKeyPath: String) = {
    val translator = partner match {
      case "moma" => new moma.MomaLoader(path)
      case "tate" => new tate.TateLoader(path)
    }

    val privateKeyXor = PEMFileUtil.privateKeyFromFile(privateKeyPath)

    privateKeyXor match {
      case Xor.Left(err) =>
        println(s"Unable to load private key for $signingIdentity from $privateKeyPath: " +
          err + "\nStatements will not be signed.")
      case _ => ()
    }

    val signatory: Option[Signatory] = privateKeyXor
      .toOption
      .map(Signatory(signingIdentity, _))

    val blobI: Iterator[Xor[TranslationError, (PhotoBlob, RawMetadataBlob)]] =
      translator.loadPhotoBlobs(signatory)

    val graph = getGraph

    val results: Iterator[Xor[Error, Canonical]] = blobI.map { pairXor =>
      pairXor.flatMap { case (blob: PhotoBlob, raw: RawMetadataBlob) =>
        Ingress.addPhotoBlob(graph, blob, Some(raw))
      }
    }
    val errors: Iterator[Error] = results.collect { case Xor.Left(err) => err }
    val canonicals: Iterator[Canonical] = results.collect { case Xor.Right(c) => c }

    println(s"Import finished: ${canonicals.length} canonicals imported ${errors.length} errors reported (see below)")
    println(errors)
  }
}
