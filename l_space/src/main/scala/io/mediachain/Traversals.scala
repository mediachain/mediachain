package io.mediachain

import com.orientechnologies.orient.core.exception.OStorageException
import com.orientechnologies.orient.core.id.ORecordId

import java.util.UUID

object Traversals {
  import gremlin.scala._
  import Types._
  import core.GraphError
  import core.GraphError._
  import cats.data.Xor
  import shapeless.HList

  def canonicalsWithID[Labels <: HList](q: GremlinScala[Vertex, Labels], canonicalID: String): GremlinScala[Vertex, Labels] = {
    q.hasLabel[Canonical]
      .has(Canonical.Keys.canonicalID, canonicalID)
  }

  def canonicalsWithUUID[Labels <: HList](q: GremlinScala[Vertex, Labels], canonicalID: UUID): GremlinScala[Vertex, Labels] =
    canonicalsWithID(q, canonicalID.toString.toLowerCase)

  def personBlobsWithExactMatch(q: GremlinScala[Vertex, _], p: Person): GremlinScala[Vertex, _] = {
    q.hasLabel[Person]
      .has(Keys.MultiHash, p.multiHash.base58)
  }

  def imageBlobsWithExactMatch(q: GremlinScala[Vertex, _], blob: ImageBlob)
  : GremlinScala[Vertex, _] = {
    q.hasLabel[ImageBlob]
      .has(Keys.MultiHash, blob.multiHash.base58)
  }

  def rawMetadataBlobsWithExactMatch(q: GremlinScala[Vertex, _], raw: RawMetadataBlob)
  : GremlinScala[Vertex, _] = {
    q.hasLabel[RawMetadataBlob]
      .has(Keys.MultiHash, raw.multiHash.base58)
  }

  def describingOrModifyingBlobs[Labels <: shapeless.HList](q: GremlinScala[Vertex, Labels], canonical: Canonical)
  : GremlinScala[Vertex, Labels] = {
    q.hasLabel[ImageBlob] // FIXME: follow edges
  }

  def getSupersedingCanonical(gs: GremlinScala[Vertex, _])
  : GremlinScala[Vertex, _] = {
    gs.hasLabel[Canonical]
      .untilWithTraverser(t => t.get.outE(SupersededBy).notExists)
      .repeat(_.out(SupersededBy))
      .hasLabel[Canonical]
  }

  def getCanonical(gs: GremlinScala[Vertex, _]): GremlinScala[Vertex, _] = {
    gs.until(_.hasLabel[Canonical])
      .repeat(
      _.inE(ModifiedBy, DescribedBy)
        .or(_.hasNot(Keys.Deprecated), _.hasNot(Keys.Deprecated, true))
        .outV
    )
  }

  def getAuthor(gs: GremlinScala[Vertex, _]): GremlinScala[Vertex, _] = {
    val base =
      gs.untilWithTraverser { t =>
        t.get().out(AuthoredBy).exists() || t.get().in(ModifiedBy).notExists()
      }
        .repeat(_.in(ModifiedBy))
        .out(AuthoredBy)

    getSupersedingCanonical(base)
  }

  def getRootRevision(gs: GremlinScala[Vertex, _]): GremlinScala[Vertex, _] = {
    gs
      .untilWithTraverser(t => t.get().in(DescribedBy).exists)
      .repeat(_.in(ModifiedBy))
  }

  def getRawMetadataForBlob(gs: GremlinScala[Vertex, _]):
  GremlinScala[Vertex, _] = {
    gs.out(TranslatedFrom)
  }

  def getSubtree(gs: GremlinScala[Vertex, _], stepLabel: StepLabel[Graph]): GremlinScala[Vertex, _] = {
      gs
      .untilWithTraverser(t => (t.get.outE(DescribedBy).notExists()
        && t.get.outE(ModifiedBy).notExists()
        && t.get.outE(AuthoredBy).notExists()))
      .repeat(_.outE.subgraph(stepLabel).inV)
  }

  implicit class VertexImplicits(v: Vertex) {
    /**
      * 'lift' a Vertex into a GremlinScala[Vertex, _] pipeline
      *
      * @return a query pipeline based on the vertex, or an InvalidElementId
      *         error if the vertex has an invalid or temporary id.
      *         This will happen if, e.g. the vertex was created during a
      *         transaction that has not been committed yet.
      */
    def toPipeline: Xor[InvalidElementId, GremlinScala[Vertex, _]] = {
      val idValidXor: Xor[InvalidElementId, Unit] = v.id match {
        case orientId: ORecordId => {
          if (orientId.isValid && (!orientId.isTemporary)) {
            Xor.right({})
          } else {
            Xor.left(InvalidElementId())
          }
        }

        case _ => Xor.right({})
      }

      idValidXor.map(_ => v.graph.V(v.id))
    }
  }

  implicit class GremlinScalaImplicits(gs: GremlinScala[Vertex, _]) {
    private def traverseAndExtract[Err <: GraphError]
    (f: GremlinScala[Vertex, _] => GremlinScala[Vertex, _])(otherwise: Err):
    Xor[Err, Vertex] = {
      val result = f(gs).headOption
      Xor.fromOption(result, otherwise)
    }

    def findCanonicalXor: Xor[CanonicalNotFound, Canonical] =
      traverseAndExtract(getCanonical) { CanonicalNotFound() }
        .map(_.toCC[Canonical])

    def findAuthorXor: Xor[CanonicalNotFound, Canonical] =
      traverseAndExtract(getAuthor) { CanonicalNotFound() }
        .map(_.toCC[Canonical])

    def findRawMetadataXor: Xor[RawMetadataNotFound, RawMetadataBlob] =
      traverseAndExtract(getRawMetadataForBlob) { RawMetadataNotFound() }
        .map(_.toCC[RawMetadataBlob])

    def findRootRevision: Xor[BlobNotFound, Vertex] =
      traverseAndExtract(getRootRevision) { BlobNotFound() }

    def findSubtreeXor: Xor[SubtreeError, Graph] = {
      val stepLabel = StepLabel[Graph]("subtree")
      val result = getSubtree(gs, stepLabel)
        .cap(stepLabel)
        .headOption
      Xor.fromOption(result, SubtreeError())
    }
  }
}
