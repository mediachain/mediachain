package io.mediachain

import cats.data.Xor
import Types._
import core.GraphError
import core.GraphError._
import gremlin.scala._

object Ingress {

  import Traversals.{GremlinScalaImplicits, VertexImplicits}

  def ingestBlobBundle(graph: Graph, bundle: BlobBundle
    , rawMetadata: Option[RawMetadataBlob] = None)
  : Xor[GraphError, Canonical] = {
    val addResultXor: Xor[GraphError, (Vertex, Canonical)] =
      bundle.content match {
        case image: ImageBlob => {
          addImageBlob(graph, image, rawMetadata)
        }

        case person: Person => {
          addPerson(graph, person, rawMetadata)
        }

        case _ => Xor.left(SubtreeError()) // TODO, better error type
      }

    addResultXor.flatMap { addResult =>
      val (vertex, canonical) = addResult

      val relationshipXors = bundle.relationships.map {
        case BlobBundle.Author(author) =>
          addPerson(graph, author, rawMetadata).flatMap { result =>
            val (_, authorCanonical) = result
            defineAuthorship(vertex, authorCanonical)
          }
      }

      relationshipXors.foldLeft(Xor.right[GraphError, Canonical](canonical)) {
        case (Xor.Left(err), _) => Xor.left(err)
        case (_, Xor.Left(err)) => Xor.left(err)
        case (_, Xor.Right(_)) => Xor.right(canonical)
      }
    }
  }


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
  def addPerson(graph: Graph, person: Person, rawMetadata: Option[RawMetadataBlob] = None):
  Xor[GraphError, (Vertex, Canonical)] = {
    // If there's an exact match already, return it,
    // otherwise create a new Person vertex and canonical
    // and return the canonical
    val q = Traversals.personBlobsWithExactMatch(graph.V, person)

    val personV: Vertex = q.headOption.getOrElse(graph + person)

    rawMetadata.foreach(attachRawMetadata(personV, _))

    val canonical = graph.V(personV.id)
      .findCanonicalXor
      .getOrElse {
        val canonicalV = graph + Canonical.create()
        canonicalV --- DescribedBy --> personV
        canonicalV.toCC[Canonical]
      }

    Xor.right((personV, canonical))
  }

  def addImageBlob(graph: Graph, image: ImageBlob, rawMetadata: Option[RawMetadataBlob] = None):
  Xor[GraphError, (Vertex, Canonical)] = {

    val imageV = Traversals.imageBlobsWithExactMatch(graph.V, image)
        .headOption.getOrElse(graph + image)

    rawMetadata.foreach(attachRawMetadata(imageV, _))

    val canonical = graph.V(imageV.id)
      .findCanonicalXor
      .getOrElse {
        val canonicalVertex = graph + Canonical.create
        canonicalVertex --- DescribedBy --> imageV
        canonicalVertex.toCC[Canonical]
      }

    Xor.right((imageV, canonical))
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

