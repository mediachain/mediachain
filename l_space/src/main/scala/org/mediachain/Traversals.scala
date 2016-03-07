package org.mediachain

object Traversals {
  import gremlin.scala._
  import Types._
  import GraphError._
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


  def getCanonical(v: Vertex): GremlinScala[Vertex, _] = {
    v.lift
      .untilWithTraverser {t =>
        t.get.label == "Canonical" ||
          t.get.in(DescribedBy, ModifiedBy).notExists
      }
      .repeat(_.in(ModifiedBy, DescribedBy))
  }

  def getAuthor(v: Vertex): GremlinScala[Vertex, _] = {
    v.lift
      .untilWithTraverser { t =>
        t.get().out(AuthoredBy).exists() || t.get().in().notExists()
      }
      .repeat(_.in(ModifiedBy))
      .out(AuthoredBy)
  }

  def getRootRevision(v: Vertex): GremlinScala[Vertex, _] = {
    v.lift
      .untilWithTraverser(t => t.get().in(DescribedBy).exists)
      .repeat(_.in(ModifiedBy))
  }

  def getRawMetadataForBlob(v: Vertex): GremlinScala[Vertex, _] = {
    v.out(TranslatedFrom)
  }

  implicit class VertexImplicits(v: Vertex) {
    /**
      * 'lift' a Vertex into a GremlinScala[Vertex, _] pipeline
      * @return a query pipeline based on the vertex
      */
    def lift: GremlinScala[Vertex, _] = v.graph.V(v.id)
  }

  implicit class GremlinScalaImplicits(gs: GremlinScala[Vertex, _]) {
    def findCanonicalXor: Xor[CanonicalNotFound, Canonical] = {
      val result = gs.flatMap(getCanonical)
        .toCC[Canonical]
        .headOption

      Xor.fromOption(result, CanonicalNotFound())
    }

    def findAuthorXor: Xor[CanonicalNotFound, Canonical] = {
      val result = gs.flatMap(getAuthor)
        .toCC[Canonical]
        .headOption

      Xor.fromOption(result, CanonicalNotFound())
    }

    def findRawMetadataXor: Xor[RawMetadataNotFound, RawMetadataBlob] = {
      val result = gs.flatMap(getRawMetadataForBlob)
        .toCC[RawMetadataBlob]
        .headOption

      Xor.fromOption(result, RawMetadataNotFound())
    }
  }
}
