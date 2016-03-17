package io.mediachain.translation.tate

import io.mediachain.translation._
import cats.data.Xor
import TranslationError.InvalidFormat
import io.mediachain.Types._

import org.json4s._
import com.fasterxml.jackson.core.JsonFactory
import java.io.File

trait TateTranslator extends Translator {
  val name = "TateCreativeCommons"
  val version = 1

  private case class Contributor(fc: String, role: String)
  private case class Artwork(title: String,
                             medium: Option[String],
                             dateText: Option[String],
                             contributors: List[Contributor])


  implicit val formats = org.json4s.DefaultFormats
  def translate(source: String): Xor[TranslationError, (PhotoBlob, RawMetadataBlob)] = {
    val jf = new JsonFactory
    val parser = jf.createParser(new File(source))

    JsonLoader.parseJOBject(parser)
      .leftMap(err =>
        TranslationError.ParsingFailed(new RuntimeException(err)))
      .flatMap(loadArtwork)
      .map(photoBlob => {
        (photoBlob, RawMetadataBlob(None, source))
      })
  }

  def loadArtwork(obj: JObject): Xor[TranslationError, PhotoBlob] = {
    val artwork = obj.extractOpt[Artwork]
    val result = artwork.map { a =>

      val artists = for {
        c <- a.contributors
        if c.role == "artist"
      } yield Person(None, c.fc)

      PhotoBlob(None,
        a.title,
        a.medium.getOrElse(""),
        a.dateText.getOrElse(""),
        artists.headOption)
    }

    Xor.fromOption(result, InvalidFormat())
  }
}

object TateLoader extends TateTranslator with DirectoryWalkerLoader
