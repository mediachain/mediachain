package org.mediachain.translation.tate

object TateTranslator {

  import cats.data.Xor
  import org.mediachain.translation.{TranslationError, InvalidFormatError}
  import org.mediachain.Types.{Person, PhotoBlob}

  import org.json4s._
  implicit val formats = org.json4s.DefaultFormats


  case class Contributor(fc: String, role: String)
  case class Artwork(title: String,
                     contributors: List[Contributor])


  def loadArtwork(obj: JObject): TranslationError Xor (PhotoBlob, List[Person]) = {
    val artwork = obj.extractOpt[Artwork]
    val result = artwork.map { a =>

      val artists = for {
        c <- a.contributors
        if c.role == "artist"
      } yield Person(None, c.fc)

      // TODO: description and date fields
      (PhotoBlob(None, a.title, "", "", artists.headOption), artists.toList)
    }

    Xor.fromOption(result, InvalidFormatError())
  }

}
