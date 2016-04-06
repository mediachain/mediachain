package io.mediachain

import io.mediachain.BlobBundle.Author
import io.mediachain.Types._
import io.mediachain.signatures.Signatory



case class BlobBundle(
  content: MetadataBlob,
  relationships: BlobBundle.BlobRelationship*
) {
  def withSignature(signatory: Signatory): BlobBundle = {
    val signedRelationships = this.relationships.map {
      case Author(person) => Author(person.withSignature(signatory))
    }

    BlobBundle(this.content.withSignature(signatory), signedRelationships: _*)
  }

}

object BlobBundle {
  sealed trait BlobRelationship
  case class Author(person: Person) extends BlobRelationship

  def imageWithAuthor(image: ImageBlob, author: Person): BlobBundle =
    BlobBundle(image, Author(author))

}
