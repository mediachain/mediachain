package io.mediachain.translation

import cats.data.Xor
import io.mediachain.Types.{RawMetadataBlob, PhotoBlob}
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory

trait Translator {
  val name: String
  val version: Int
  def loadPhotoBlobs(path: String): Iterable[Xor[TranslationError,(PhotoBlob, RawMetadataBlob)]]
}

object TranslatorDispatcher {
  def dispatch(partner: String, path: String) = {
    //println("partner: " + partner + ", path: " + path)
    val translator: Translator = partner match {
      case "moma" => moma.MomaTranslator
      case "tate" => tate.TateTranslator
    }

    val blobS = translator.loadPhotoBlobs(path)

    val url = sys.env.get("ORIENTDB_URL").getOrElse(throw new Exception("ORIENTDB_URL required"))
    val user = sys.env.get("ORIENTDB_USER").getOrElse(throw new Exception("ORIENTDB_USER required"))
    val password = sys.env.get("ORIENTDB_PASSWORD").getOrElse(throw new Exception("ORIENTDB_PASSWORD required"))
    val graph = new OrientGraphFactory(url, user, password).getNoTx()
  }
}
