package org.mediachain

import cats.data.Xor
import org.mediachain.Types._
import gremlin.scala._

object Ingress {
  import Traversals.{GremlinScalaImplicits, VertexImplicits}

  def attachRawMetadata(blobV: Vertex, raw: RawMetadataBlob): Xor[Throwable, Unit] = {
    val graph = blobV.graph

    // only allow one TranslatedFrom edge from each blob vertex
    if (blobV.lift.findRawMetadataOption.isEmpty) {
      // add the raw metadata to the graph if it doesn't already exist
      val rawV = Traversals.rawMetadataBlobsWithExactMatch(graph.V, raw)
        .headOption
        .getOrElse(graph + raw)

      blobV --- TranslatedFrom --> rawV
      Xor.right(())
    } else {
      Xor.left(new Exception("Only one RawMetadataBlob is allowed per blob revision"))
    }
  }

  def defineAuthorship(blobV: Vertex, authorCanonical: Canonical): Xor[Throwable, Unit] = {

    Xor.catchNonFatal {
      val authorCanonicalV = authorCanonical.vertex(blobV.graph)
          .getOrElse(throw new Exception("No vertex for author Canonical exists in graph"))

      val existingAuthors = Traversals.getAuthor(blobV).toSet
      if (!existingAuthors.contains(authorCanonicalV)) {
        blobV --- AuthoredBy --> authorCanonicalV
      }
    }
  }

  // throws?
  def addPerson(graph: Graph, author: Person, raw: Option[RawMetadataBlob] = None): Canonical = {
    // If there's an exact match already, return it,
    // otherwise create a new Person vertex and canonical
    // and return the canonical
    val q = Traversals.personBlobsWithExactMatch(graph.V, author)

    val personV: Vertex = q.headOption.getOrElse(graph + author)
    raw.foreach(attachRawMetadata(personV, _))

    graph.V(personV.id)
      .findCanonicalOption
      .getOrElse {
        val canonicalV = graph + Canonical.create()
        canonicalV --- DescribedBy --> personV
        canonicalV.toCC[Canonical]
      }
  }

  def addPhotoBlob(graph: Graph, photo: PhotoBlob, raw: Option[RawMetadataBlob] = None): Canonical = {
    // extract author & add if they don't exist in the graph already
    val author: Option[Canonical] = photo.author.map { p =>
      addPerson(graph, p, raw)
    }

    // check to see if a duplicate entry exists
    val photoV = Traversals.photoBlobsWithExactMatch(graph.V, photo)
        .headOption.getOrElse(graph + photo)

    // attach raw metadata (if it exists) to photo vertex
    raw.foreach(attachRawMetadata(photoV, _))

    // if there's an author, ensure that there's an edge defining the relationship
    author.foreach(defineAuthorship(photoV, _))

    // return existing canonical for photo vertex, or create one
    graph.V(photoV.id)
      .findCanonicalOption
      .getOrElse {
        val canonicalVertex = graph + Canonical.create
        canonicalVertex --- DescribedBy --> photoV
        canonicalVertex.toCC[Canonical]
      }
  }


  def modifyPhotoBlob(graph: Graph, parentVertex: Vertex, photo: PhotoBlob): Option[Canonical] = {
    Traversals.photoBlobsWithExactMatch(graph.V, photo)
      .findCanonicalOption
      .orElse {
        val childVertex = graph + photo
        parentVertex --- ModifiedBy --> childVertex

        val author: Option[Canonical] = photo.author.map { p =>
          addPerson(graph, p)
        }

        val existingAuthor = Traversals.getAuthor(childVertex).toCC[Canonical].headOption

        (author, existingAuthor) match {
          case (Some(newAuthor), Some(oldAuthor)) => {
            if (newAuthor.canonicalID != oldAuthor.canonicalID) {
              defineAuthorship(childVertex, newAuthor)
            }
          }
          case _ => ()
        }

        childVertex.lift.findCanonicalOption
      }
  }
}

