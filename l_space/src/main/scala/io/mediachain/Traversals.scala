package io.mediachain

import com.orientechnologies.orient.core.id.ORecordId
import java.util.UUID

import org.apache.tinkerpop.gremlin.process.traversal.step.util.BulkSet
import shapeless.HNil
import scala.collection.JavaConverters._

object Traversals {
  import gremlin.scala._
  import Types._
  import core.GraphError
  import core.GraphError._
  import cats.data.Xor
  import shapeless.HList

  def canonicalsWithID[Labels <: HList]
  (q: GremlinScala[Vertex, Labels], canonicalID: String, allowSuperseded: Boolean = false): GremlinScala[Vertex, Labels] =
    {
      val base =
        q.hasLabel[Canonical]
          .has(Canonical.Keys.canonicalID, canonicalID)
      if (allowSuperseded) {
        base
      } else {
        getCanonical(base)
      }
    }


  def canonicalsWithUUID[Labels <: HList]
  (q: GremlinScala[Vertex, Labels], canonicalID: UUID, allowSuperseded: Boolean = false): GremlinScala[Vertex, Labels] =
    canonicalsWithID(q, canonicalID.toString.toLowerCase, allowSuperseded)

  def personBlobsWithExactMatch[Labels <: HList]
  (q: GremlinScala[Vertex, Labels], p: Person): GremlinScala[Vertex, Labels] =
    q.hasLabel[Person]
      .has(Keys.MultiHash, p.multiHash.base58)


  def imageBlobsWithExactMatch[Labels <: HList]
  (q: GremlinScala[Vertex, Labels], blob: ImageBlob): GremlinScala[Vertex, Labels] =
    q.hasLabel[ImageBlob]
      .has(Keys.MultiHash, blob.multiHash.base58)


  def rawMetadataBlobsWithExactMatch[Labels <: HList]
  (q: GremlinScala[Vertex, Labels], raw: RawMetadataBlob)
  : GremlinScala[Vertex, Labels] =
    q.hasLabel[RawMetadataBlob]
      .has(Keys.MultiHash, raw.multiHash.base58)

  def describingOrModifyingBlobs[Labels <: HList]
  (q: GremlinScala[Vertex, Labels], canonical: Canonical)
  : GremlinScala[Vertex, Labels] = {
    canonicalsWithID(q, canonical.canonicalID)
      .out(DescribedBy).aggregate("blobs")
      .until(_.not(_.out(ModifiedBy)))
      .repeat(_.out(ModifiedBy).aggregate("blobs"))
      .cap("blobs")
      .unfold[Vertex]
  }

  def getSupersedingCanonical[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels])
  : GremlinScala[Vertex, Labels] =
    gs.hasLabel[Canonical]
      .untilWithTraverser(t => t.get.outE(SupersededBy).notExists)
      .repeat(_.out(SupersededBy))
      .hasLabel[Canonical]


  def getAllCanonicalsIncludingSuperseded[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels])
  : GremlinScala[Vertex, Labels] = {
        getCanonical(gs).aggregate("canonicals")
          .until(_.not(_.inE(SupersededBy)))
          .repeat(_.in(SupersededBy).aggregate("canonicals"))
          .cap("canonicals")
          .unfold[Vertex]
  }

  def getCanonical[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] =
    gs.until(_.hasLabel[Canonical])
      .repeat(
      _.inE(ModifiedBy, DescribedBy)
        .or(_.hasNot(Keys.Deprecated), _.hasNot(Keys.Deprecated, true))
        .outV
    )


  def getAuthor[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] = {
    val base =
      gs.untilWithTraverser { t =>
        t.get().out(AuthoredBy).exists() || t.get().in(ModifiedBy).notExists()
      }
        .repeat(_.in(ModifiedBy))
        .out(AuthoredBy)

    getSupersedingCanonical(base)
  }

  def getWorks[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] = {
    val works =
      getAllCanonicalsIncludingSuperseded(gs)
      .in(AuthoredBy)

    getCanonical(works)
  }

  def getRootRevision[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] =
    gs.untilWithTraverser(t => t.get().in(DescribedBy).exists)
      .repeat(_.in(ModifiedBy))


  def getRawMetadataForBlob[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]):
  GremlinScala[Vertex, Labels] =
    gs.out(TranslatedFrom)


  def getSubtree[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels], stepLabel: StepLabel[Graph])
  : GremlinScala[Vertex, Labels] =
    gs.untilWithTraverser(t => (t.get.outE(DescribedBy).notExists()
      && t.get.outE(ModifiedBy).notExists()
      && t.get.outE(AuthoredBy).notExists())
    ).repeat(_.outE.subgraph(stepLabel).inV)


  implicit class VertexImplicits(v: Vertex) {
    /**
      * 'lift' a Vertex into a GremlinScala[Vertex, _] pipeline
      *
      * @return a query pipeline based on the vertex, or an InvalidElementId
      *         error if the vertex has an invalid or temporary id.
      *         This will happen if, e.g. the vertex was created during a
      *         transaction that has not been committed yet.
      */
    def toPipeline: Xor[InvalidElementId, GremlinScala[Vertex, HNil]] = {
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

  implicit class GremlinScalaImplicits[Labels <: HList](gs: GremlinScala[Vertex, Labels]) {
    private def traverseAndExtract[Err <: GraphError]
    (f: GremlinScala[Vertex, Labels] => GremlinScala[Vertex, Labels])(otherwise: Err):
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

    // can this fail, or just return an empty list?
    // TODO: either handle errors or remove the Xor
    def findWorksXor: Xor[GraphError, List[Canonical]] =
      Xor.right(
        getWorks(gs)
        .toCC[Canonical]
        .toList
      )

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
