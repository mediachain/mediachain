package io.mediachain

import cats.data.Xor
import Types._
import core.GraphError
import core.GraphError._
import gremlin.scala._

object Ingress {
  import Traversals.{GremlinScalaImplicits, VertexImplicits}
  import io.mediachain.util.GremlinUtils._

  def attachRawMetadata(blobV: Vertex, raw: RawMetadataBlob): Unit = {
    val graph = blobV.graph

    // add the raw metadata to the graph if it doesn't already exist
    val rawV = Traversals.rawMetadataBlobsWithExactMatch(graph.V, raw)
      .headOption
      .getOrElse(graph + raw)

    // check if there's already an edge from the blob vertex to the raw metadata vertex
    val existingBlobs = rawV.lift.in(TranslatedFrom).toSet
    if (!existingBlobs.contains(blobV)) {
      blobV --- TranslatedFrom --> rawV
    }
  }

  def defineAuthorship(blobV: Vertex, authorCanonical: Canonical):
  Xor[AuthorNotFoundError, Unit] = {
    authorCanonical.vertex(blobV.graph).map { authorCanonicalV =>
      val existingAuthor = Traversals.getAuthor(blobV.lift).headOption

      if (!existingAuthor.contains(authorCanonicalV)) {
        blobV --- AuthoredBy --> authorCanonicalV
      }

      Xor.right({})
    }.getOrElse {
      Xor.left(AuthorNotFoundError(blobV))
    }
  }

  // throws?
  def addPerson(graph: Graph,
                author: Person,
                raw: Option[RawMetadataBlob] = None):
  Xor[GraphError, Canonical] = {
    // If there's an exact match already, return it,
    // otherwise create a new Person vertex and canonical
    // and return the canonical
    val q = Traversals.personBlobsWithExactMatch(graph.V, author)

    val personV: Vertex = q.headOption.getOrElse(graph + author)

    raw.foreach(attachRawMetadata(personV, _))

    graph.V(personV.id)
      .findCanonicalXor
      .orElse {
        val canonicalV = graph + Canonical.create()
        canonicalV --- DescribedBy --> personV
        Xor.right(canonicalV.toCC[Canonical])
      }
  }

  def addImageBlob(graph: Graph,
                   photo: ImageBlob,
                   raw: Option[RawMetadataBlob] = None):
  Xor[GraphError, Canonical] = {

    val photoV = Traversals.imageBlobsWithExactMatch(graph.V, photo)
        .headOption.getOrElse(graph + photo)

    raw.foreach(attachRawMetadata(photoV, _))

      graph.V(photoV.id)
        .findCanonicalXor
        .orElse {
          val canonicalVertex = graph + Canonical.create
          canonicalVertex --- DescribedBy --> photoV
          Xor.right(canonicalVertex.toCC[Canonical])
        }
  }


  def modifyImageBlob(graph: Graph, parentVertex: Vertex, photo: ImageBlob, raw: Option[RawMetadataBlob] = None):
  Xor[GraphError, Canonical] = {
    Traversals.imageBlobsWithExactMatch(graph.V, photo)
      .findCanonicalXor
      .map(Xor.right)
      .getOrElse {
        val childVertex = graph + photo
        parentVertex --- ModifiedBy --> childVertex

        raw.foreach(attachRawMetadata(childVertex, _))

        childVertex.lift.findCanonicalXor
          .map(Xor.right)
          .getOrElse(Xor.left(CanonicalNotFound()))
      }
  }
}

