package io.mediachain

import com.orientechnologies.orient.core.id.ORecordId
import java.util.UUID

import shapeless.HNil
import gremlin.scala._
import Types._
import core.GraphError
import core.GraphError._
import cats.data.Xor
import shapeless.HList

object Traversals {

  object Implicits {

    /** Type that is implicitly convertible from all types *except* for
      * GremlinScala.  This is used to constrain the `>>` operator so that
      * it cannot be used to return another GremlinScala pipeline, only to
      * extract a value from a pipeline at the end of a chain.
      *
      * This gives us a clear distinction between the `~>` and `>>` operators.
      * The `~>` operator is used to chain together GremlinScala operations,
      * while `>>` is used to extract a value at the end.
     */
    sealed trait NotAGremlinScala[T]
    implicit def anything[T <: Any] = new NotAGremlinScala[T] {}

    /**
      * Together with `conflictForGremlinScala2`, prevents a value of
      * `GremlinScala` from being implicitly converted to `NotAGremlinScala`.
      *
      * This works because the compiler will not apply an implicit conversion
      * if there are two ambiguous implicits in scope.
      *
      * Because this is a hack, the resulting compiler error is not helpful;
      * hopefully this comment will clear things up a bit :)
      */
    implicit def conflictForGremlinScala[T <: GremlinScala[_, _]] =
      new NotAGremlinScala[T] {}

    /**
      * @see conflictForGremlinScala
      */
    implicit def conflictForGremlinScala2[T <: GremlinScala[_, _]] =
      new NotAGremlinScala[T] {}


    implicit class GremlinScalaImplicits[End, Labels <: HList]
    (val gs: GremlinScala[End, Labels]) extends AnyVal
    {
      /**
        * Chain together query pipeline operations.
        *
        * e.g.
        *
        * graph.V ~> personsBlobsWithExactMatch(pablo) ~> getCanonical
        *
        * The right-hand side of the ~> operator must be a function of
        * `GremlinScala[A, InLabels] => GremlinScala[B, OutLabels]`
        *
        * The `A` and `B` type parameters represent the type of value that's
        * at the "end" of the pipeline; they can be the same type or different
        * depending on the operation.
        *
        * Likewise, the `Labels` and `OutLabels` type params may be the same
        * concrete type or different types.
        *
        * This can be used with partially applied (curried) functions, e.g.:
        *
        * def findTheMcGuffin[Labels <: HList]
        *   (foo: String)(gs: GremlinScala[Vertex, Labels])
        * : GremlinScala[Vertex, Labels] = gs.has('foo', foo)
        *
        * can be chained by partially applying the method:
        * graph.V ~> findTheMcGuffin("bar")
        *
        * Note that to call methods on the resulting pipeline, you'll either
        * need to wrap the chain in parens:
        * (graph.V ~> findKittens).headOption
        *
        * or, you can use the >> operator with an anonymous function:
        * graph.V ~> findKittens >> (_.headOption)
        *
        */
      def ~>[OutEnd, OutLabels <: HList]
      (f: GremlinScala[End, Labels] => GremlinScala[OutEnd, OutLabels]):
      GremlinScala[OutEnd, OutLabels] = f(gs)


      /**
        * Apply a function to a query pipeline, returning some result
        *
        * The result can be of any type *except* for `GremlinScala` -
        * use `~>` to return a `GremlinScala` from an existing `GremlinScala`
        * pipeline.
        *
        * e.g.
        * val vertexXor: Xor[VertexNotFound, Vertex] =
        *   graph.V ~> canonicalsWithID(someID) >> headXor
        *
        * Can also be used to call built-in GremlinScala methods on the final
        * result of a ~> query chain by combining with an anonymous function:
        *
        * graph.V ~> canonicalsWithID(someID) >> (_.headOption)
        */
      def >>[T: NotAGremlinScala](f: GremlinScala[End, Labels] => T): T = f(gs)
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
      def toPipeline: Xor[InvalidElementId, GremlinScala[Vertex, HNil]] =
        v.id match {
          case orientId: ORecordId => {
            if (orientId.isValid && (!orientId.isTemporary)) {
              Xor.right(v.graph.V(v.id))
            } else {
              Xor.left(InvalidElementId())
            }
          }
          case _ => Xor.left(InvalidElementId())
        }
    }
  }


  import Implicits._

  // Extractions - use with the >> operator to pull a value out of a pipeline

  def headXor[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]): Xor[VertexNotFound, Vertex] =
    Xor.fromOption(gs.headOption, VertexNotFound())


  def typedHeadXor[L, R, Labels <: HList](ifNone: => L, fromVertex: Vertex => R)
    (gs: GremlinScala[Vertex, Labels]): Xor[L, R] =
    headXor(gs).bimap({_ => ifNone}, fromVertex)


  def findCanonicalXor[Labels <: HList](gs: GremlinScala[Vertex, Labels])
  : Xor[CanonicalNotFound, Canonical] =
    gs ~> getCanonical >>
      typedHeadXor(CanonicalNotFound(), _.toCC[Canonical])


  def findAuthorXor[Labels <: HList](gs: GremlinScala[Vertex, Labels])
  : Xor[CanonicalNotFound, Canonical] =
    gs ~> getAuthor >>
      typedHeadXor(CanonicalNotFound(), _.toCC[Canonical])


  def findRawMetadataXor[Labels <: HList](gs: GremlinScala[Vertex, Labels])
  : Xor[RawMetadataNotFound, RawMetadataBlob] =
    gs ~> getRawMetadataForBlob >>
      typedHeadXor(RawMetadataNotFound(), _.toCC[RawMetadataBlob])


  def findRootRevisionXor[Labels <: HList](gs: GremlinScala[Vertex, Labels])
  : Xor[BlobNotFound, Vertex] =
    gs ~> getRootRevision >>
      typedHeadXor(BlobNotFound(), identity)


  // can this fail, or just return an empty list?
  // TODO: either handle errors or remove the Xor
  def findWorksXor[Labels <: HList](gs: GremlinScala[Vertex, Labels])
  : Xor[GraphError, List[Canonical]] =
    Xor.right(
      getWorks(gs)
        .toCC[Canonical]
        .toList
    )


  def findSubtreeXor[Labels <: HList](gs: GremlinScala[Vertex, Labels])
  : Xor[SubtreeError, Graph] = {
    val stepLabel = StepLabel[Graph]("subtree")
    val result = getSubtree(gs, stepLabel)
      .cap(stepLabel)
      .headOption
    Xor.fromOption(result, SubtreeError())
  }


  // Operations - can be chained together with the ~> operator

  def canonicalsWithID[Labels <: HList]
  (canonicalID: String, allowSuperseded: Boolean = false)
    (q: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] =
  {
    val base =
      q.hasLabel[Canonical].has(Canonical.Keys.canonicalID, canonicalID)

    if (allowSuperseded) base
    else base ~> getSupersedingCanonical
  }


  def canonicalsWithUUID[Labels <: HList]
  (canonicalID: UUID, allowSuperseded: Boolean = false)
    (q: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] =
    q ~> canonicalsWithID(canonicalID.toString.toLowerCase, allowSuperseded)


  def getCanonical[Labels <: HList]
  (gs: GremlinScala[Vertex, Labels]): GremlinScala[Vertex, Labels] =
    gs.until(_.hasLabel[Canonical])
      .repeat(
        _.inE(ModifiedBy, DescribedBy)
          .or(_.hasNot(Keys.Deprecated), _.hasNot(Keys.Deprecated, true))
          .outV
      )


  def personBlobsWithExactMatch[Labels <: HList]
  (p: Person)(q: GremlinScala[Vertex, Labels])
  : GremlinScala[Vertex, Labels] =
    q.hasLabel[Person]
      .has(Keys.MultiHash, p.multiHash.base58)


  def imageBlobsWithExactMatch[Labels <: HList]
  (blob: ImageBlob)(q: GremlinScala[Vertex, Labels])
  : GremlinScala[Vertex, Labels] =
    q.hasLabel[ImageBlob]
      .has(Keys.MultiHash, blob.multiHash.base58)


  def rawMetadataBlobsWithExactMatch[Labels <: HList]
  (raw: RawMetadataBlob)(q: GremlinScala[Vertex, Labels])
  : GremlinScala[Vertex, Labels] =
    q.hasLabel[RawMetadataBlob]
      .has(Keys.MultiHash, raw.multiHash.base58)


  def describingOrModifyingBlobs[Labels <: HList]
  (canonical: Canonical)(q: GremlinScala[Vertex, Labels])
  : GremlinScala[Vertex, Labels] = {
    canonicalsWithID(canonical.canonicalID)(q)
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

}
