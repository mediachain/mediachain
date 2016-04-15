package io.mediachain

import cats.data.Xor
import Types._
import core.GraphError
import core.GraphError._
import gremlin.scala._
import io.mediachain.util.GremlinUtils._
import Traversals._
import Traversals.Implicits._


object Ingress {

  case class BlobAddResult(blobVertex: Vertex, canonicalVertex: Vertex)
  object BlobAddResult {
    def apply(t: Tuple2[Vertex, Vertex]): BlobAddResult =
      BlobAddResult(t._1, t._2)
  }

  def ingestBlobBundle(graph: Graph, bundle: BlobBundle
    , rawMetadata: Option[RawMetadataBlob] = None)
  : Xor[GraphError, Canonical] = withTransactionXor(graph) {
    val addResultXor: Xor[GraphError, BlobAddResult] =
      bundle.content match {
        case image: ImageBlob => {
          addImageBlob(graph, image, rawMetadata)
        }

        case person: Person => {
          addPerson(graph, person, rawMetadata)
        }

        case _ => Xor.left(InvalidBlobBundle())
      }

    addResultXor.flatMap { addResult =>
      val (blobV, canonicalV) = (addResult.blobVertex, addResult.canonicalVertex)

      val relationshipXors = bundle.relationships.map {
        case BlobBundle.Author(author) =>
          addPerson(graph, author, rawMetadata).flatMap { result =>
            defineAuthorship(blobV, result.canonicalVertex)
          }
      }

      val canonical = canonicalV.toCC[Canonical]
      relationshipXors.foldLeft(Xor.right[GraphError, Canonical](canonical)) {
        case (Xor.Left(err), _) => Xor.left(err)
        case (_, Xor.Left(err)) => Xor.left(err)
        case (_, Xor.Right(_)) => Xor.right(canonical)
      }
    }
  }


  def attachRawMetadata(blobV: Vertex, raw: RawMetadataBlob)
  : Xor[GraphError, Unit] =
  withTransaction(blobV.graph) {
    val graph = blobV.graph

    // add the raw metadata to the graph if it doesn't already exist
    val rawVXor = graph.V ~> rawMetadataBlobsWithExactMatch(raw) >> headXor
    val rawV = rawVXor.getOrElse(graph + raw)

    val edgeAlreadyExists = blobV.toPipeline
      .map(_.out(TranslatedFrom).toSet.contains(rawV))
      .valueOr(_ => false)

    if (!edgeAlreadyExists) {
      blobV --- TranslatedFrom --> rawV
    }
  }

  def defineAuthorship(blobV: Vertex, authorCanonicalVertex: Vertex):
  Xor[GraphError, Unit] = withTransaction(blobV.graph) {

    val existingAuthorXor = for {
      q <- blobV.toPipeline
      author <- q >> findAuthorXor
    } yield author

    val authorshipAlreadyDefined = existingAuthorXor.exists(
      _.canonicalID == authorCanonicalVertex.toCC[Canonical].canonicalID
    )

    if (!authorshipAlreadyDefined) {
      blobV --- AuthoredBy --> authorCanonicalVertex
    }
  }



  def addMetadataBlob[T <: MetadataBlob with Product : Marshallable]
  (graph: Graph, blob: T, rawMetadataOpt: Option[RawMetadataBlob] = None):
  Xor[GraphError, BlobAddResult] = withTransactionXor(graph) {

    val existingVertex: Xor[VertexNotFound, Vertex] = blob match {
      case image: ImageBlob =>
        graph.V ~> imageBlobsWithExactMatch(image) >> headXor
      case person: Person =>
        graph.V ~> personBlobsWithExactMatch(person) >> headXor
      case _ => Xor.left(VertexNotFound())
    }

    val resultForExistingVertex: Xor[GraphError, BlobAddResult] =
      for {
        v <- existingVertex
        pipeline <- v.toPipeline
        canonicalV <- Xor.fromOption(
          Traversals.getCanonical(pipeline).headOption,
          CanonicalNotFound())
      } yield BlobAddResult(v, canonicalV)

    val resultXor: Xor[GraphError, BlobAddResult] =
      resultForExistingVertex
      .orElse {
        val v = graph + blob
        val canonicalV = graph + Canonical.create()
        canonicalV --- DescribedBy --> v
        Xor.right(BlobAddResult(v, canonicalV))
      }

    rawMetadataOpt.foreach { raw: RawMetadataBlob =>
      resultXor.foreach { res: BlobAddResult =>
        attachRawMetadata(res.blobVertex, raw)
      }
    }

    resultXor
  }


  def addPerson(graph: Graph, person: Person, rawMetadata: Option[RawMetadataBlob] = None):
  Xor[GraphError, BlobAddResult] =
    addMetadataBlob(graph, person, rawMetadata)


  def addImageBlob(graph: Graph, image: ImageBlob, rawMetadata: Option[RawMetadataBlob] = None):
  Xor[GraphError, BlobAddResult] =
    addMetadataBlob(graph, image, rawMetadata)


  def modifyImageBlob(graph: Graph, parentVertex: Vertex, image: ImageBlob, raw: Option[RawMetadataBlob] = None):
  Xor[GraphError, BlobAddResult] = withTransactionXor(graph) {

    val childVertexXor = graph.V ~> imageBlobsWithExactMatch(image) >> headXor

    val childVertex = childVertexXor
      .getOrElse {
        val childV = graph + image
        parentVertex --- ModifiedBy --> childV
        childV
      }

    val canonicalXor =
      parentVertex.toPipeline.flatMap { gs: GremlinScala[Vertex, _] =>
        Xor.fromOption(gs.headOption, CanonicalNotFound())
      }

    canonicalXor.map(canonicalV => BlobAddResult(childVertex, canonicalV))
  }
}

