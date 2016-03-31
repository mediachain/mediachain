package io.mediachain.translation.tate

import io.mediachain.translation._
import cats.data.Xor
import io.mediachain.core.TranslationError
import io.mediachain.core.TranslationError.InvalidFormat
import io.mediachain.Types._

import org.json4s._

object TateTranslator extends Translator {
  val name = "TateCreativeCommons"
  val version = 1

  private case class Contributor(fc: String, role: String)
  private case class Artwork(title: String,
                             medium: Option[String],
                             dateText: Option[String],
                             contributors: List[Contributor],
                             id: Int)

  def translate(json: JObject): Xor[TranslationError, ImageBlob] = {
    implicit val formats = org.json4s.DefaultFormats
    val artwork = json.extractOpt[Artwork]
    val result = artwork.map { a =>

      val artists = for {
        c <- a.contributors
        if c.role == "artist"
      } yield Person(None, c.fc)

      ImageBlob(None,
        a.title,
        a.medium.getOrElse(""),
        a.dateText.getOrElse(""),
        artists.headOption,
        external_ids = Map("tate:id" -> a.id.toString)
      )
    }

    Xor.fromOption(result, InvalidFormat())
  }
}

class TateLoader(val path: String, implicit val translator: TateTranslator.type = TateTranslator) extends DirectoryWalkerLoader[TateTranslator.type]
