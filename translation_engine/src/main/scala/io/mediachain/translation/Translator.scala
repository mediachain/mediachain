package io.mediachain.translation

import java.io.File
import io.mediachain.translation.TranslationError.ParsingFailed

import scala.io.Source
import cats.data.Xor
import io.mediachain.Types.{RawMetadataBlob, PhotoBlob}
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
import org.json4s._
import com.fasterxml.jackson.core.JsonFactory

trait Translator {
  val name: String
  val version: Int
  def translate(source: JObject): Xor[TranslationError, PhotoBlob]
}

trait FSLoader {
  def loadPhotoBlobs: Iterator[(Xor[TranslationError,PhotoBlob], RawMetadataBlob)]
  val rawI: Iterator[String]
  val jsonI: Iterator[Xor[TranslationError, JObject]]
}

trait DirectoryWalkerLoader extends FSLoader {
  self: Translator =>
  val path: String
  val fileI: Iterator[File] = DirectoryWalker.findWithExtension(new File(path), ".json").iterator
  val rawI = fileI.map(Source.fromFile(_).mkString)
  val jsonI = fileI.map { file =>
      val jf = new JsonFactory
      val parser = jf.createParser(file)
      JsonLoader.parseJOBject(parser)
        .leftMap(err =>
          TranslationError.ParsingFailed(new RuntimeException(err)))
    }

  def loadPhotoBlobs: Iterator[(Xor[TranslationError,PhotoBlob], RawMetadataBlob)] = {
    val combinedI: Iterator[(Xor[ParsingFailed, JObject], String)] = jsonI.zip(rawI)
    combinedI.map { case (jsonXor, raw) =>
      (jsonXor.flatMap(translate), RawMetadataBlob(None, raw))
    }
  }
}

object TranslatorDispatcher {
  def dispatch(partner: String, path: String) = {
    //println("partner: " + partner + ", path: " + path)
    val translator: FSLoader = partner match {
      //case "moma" => moma.MomaTranslator
      case "tate" => new tate.TateLoader(path)
    }

    val blobS = translator.loadPhotoBlobs

    val url = sys.env.get("ORIENTDB_URL").getOrElse(throw new Exception("ORIENTDB_URL required"))
    val user = sys.env.get("ORIENTDB_USER").getOrElse(throw new Exception("ORIENTDB_USER required"))
    val password = sys.env.get("ORIENTDB_PASSWORD").getOrElse(throw new Exception("ORIENTDB_PASSWORD required"))
    val graph = new OrientGraphFactory(url, user, password).getNoTx()
  }
}
