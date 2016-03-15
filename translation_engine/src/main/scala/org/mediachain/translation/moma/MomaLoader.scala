package org.mediachain.translation.moma

import java.io.File

import cats.data.{Streaming, Xor}
import com.fasterxml.jackson.core.JsonFactory
import org.json4s._
import org.mediachain.Types.{Person, PhotoBlob}
import org.mediachain.translation.JsonLoader.parseJArray

object MomaLoader {
  /** A convenience case class for parsing JSON blobs into a more abstract form.
    *
    * @param Title Title of the work
    * @param Medium Medium of the work (becomes description for now)
    * @param Date Date of the work
    * @param Artist Artist of the work
    */
  case class MomaPhotoBlob(Title: String,
                           Medium: String,
                           Date: String,
                           Artist: String) {
    def asPhotoBlob: PhotoBlob = PhotoBlob(
      id = None,
      title = Title,
      description = Medium,
      date = Date,
      author = Some(Person(id = None, Artist))
    )
  }

  /** Casts a JValue to a PhotoBlob using `extract` and `asPhotoBlob`
    *
    * @param json The JValue representing a work
    * @return A `PhotoBlob` extracted from the JValue
    */
  def jsonToPhotoBlob(json: JValue): PhotoBlob = {
    implicit val formats = DefaultFormats
    json.extract[MomaPhotoBlob].asPhotoBlob
  }

  /** Given a filename representing a MoMA-schema list of artworks, parse the
    * file into a stream of `PhotoBlob`s.
    *
    * @param filename The filename to read and parse
    * @return A stream of `PhotoBlob`s
    */
  def loadPhotoBlobs(filename: String): Streaming[PhotoBlob] = {
    val jf = new JsonFactory
    val parser = jf.createParser(new File(filename))

    parser.nextToken

    parseJArray(parser).flatMap {
      case Xor.Right(json) => Streaming(jsonToPhotoBlob(json))
      case _ => Streaming.empty
    }
  }
}

