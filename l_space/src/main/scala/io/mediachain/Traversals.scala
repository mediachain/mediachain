package io.mediachain

object Traversals {
  import gremlin.scala._
  import Types._
  import core.GraphError
  import core.GraphError._
  import cats.data.Xor

  def canonicalsWithID(q: GremlinScala[Vertex, _], canonicalID: String): GremlinScala[Vertex, _] = {
    q.hasLabel[Canonical]
      .has(Canonical.Keys.canonicalID, canonicalID)
  }

  def personBlobsWithExactMatch(q: GremlinScala[Vertex, _], p: Person): GremlinScala[Vertex, _] = {
    q.hasLabel[Person]
      .has(Person.Keys.name, p.name)
  }

  def photoBlobsWithExactMatch(q: GremlinScala[Vertex, _], blob: PhotoBlob)
  : GremlinScala[Vertex, _] = {
    q.hasLabel[PhotoBlob]
      .has(PhotoBlob.Keys.title, blob.title)
      .has(PhotoBlob.Keys.description, blob.description)
      .has(PhotoBlob.Keys.date, blob.date)
  }

  def rawMetadataBlobsWithExactMatch(q: GremlinScala[Vertex, _], raw: RawMetadataBlob)
  : GremlinScala[Vertex, _] = {
    q.hasLabel[RawMetadataBlob]
      .has(RawMetadataBlob.Keys.blob, raw.blob)
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
      * @return a query pipeline based on the vertex
      */
    def lift: GremlinScala[Vertex, _] = v.graph.V(v.id)
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
