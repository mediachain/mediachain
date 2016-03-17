package io.mediachain.translation

import java.io.File
import scala.io.Source
import cats.data.Xor
import io.mediachain.Types.{RawMetadataBlob, PhotoBlob}
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

trait Translator {
  val name: String
  val version: Int
  def translate(source: String): Xor[TranslationError, (PhotoBlob, RawMetadataBlob)]
}

trait FSLoader {
  def loadPhotoBlobs(path: String): Iterator[Xor[TranslationError,(PhotoBlob, RawMetadataBlob)]]
}

trait DirectoryWalkerLoader extends FSLoader {
  self: Translator =>
  def loadPhotoBlobs(path: String): Iterator[Xor[TranslationError,(PhotoBlob, RawMetadataBlob)]] = {
    val files = DirectoryWalker.findWithExtension(new File(path), ".json")
    val jsonStrings = files.map(Source.fromFile(_).mkString)
    jsonStrings.map(translate).iterator
  }
}

object TranslatorDispatcher {
  def dispatch(partner: String, path: String) = {
    //println("partner: " + partner + ", path: " + path)
    val translator: FSLoader = partner match {
      //case "moma" => moma.MomaTranslator
      case "tate" => tate.TateLoader
    }

    val blobS = translator.loadPhotoBlobs(path)

    val url = sys.env.get("ORIENTDB_URL").getOrElse(throw new Exception("ORIENTDB_URL required"))
    val user = sys.env.get("ORIENTDB_USER").getOrElse(throw new Exception("ORIENTDB_USER required"))
    val password = sys.env.get("ORIENTDB_PASSWORD").getOrElse(throw new Exception("ORIENTDB_PASSWORD required"))
    val graph = new OrientGraphFactory(url, user, password).getNoTx()
  }
}
