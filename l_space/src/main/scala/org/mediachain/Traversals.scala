package org.mediachain


object Traversals {
  import gremlin.scala._
  import Types._

  def canonicalWithID(q: GremlinScala[Vertex, _], canonicalID: String) = {
    q.hasLabel[Canonical]
      .has(Canonical.Keys.canonicalID, canonicalID)
  }

  def personWithExactMatch(q: GremlinScala[Vertex, _], p: Person) = {
    q.hasLabel[Person]
      .has(Person.Keys.name, p.name)
  }

  def photoBlobWithExactMatch(q: GremlinScala[Vertex, _], blob: PhotoBlob) = {
    q.hasLabel[PhotoBlob]
      .has(PhotoBlob.Keys.title, blob.title)
      .has(PhotoBlob.Keys.description, blob.description)
      .has(PhotoBlob.Keys.date, blob.date)
  }

  def getCanonical(v: Vertex) = {
    v.graph.V(v.id)
      .untilWithTraverser(t => t.get().in(DescribedBy).exists)
      .repeat(_.in(ModifiedBy))
      .in(DescribedBy)
  }

  def getAuthor(v: Vertex) = {
    v.graph.V(v.id)
      .untilWithTraverser { t =>
        t.get().out(AuthoredBy).exists() || t.get().in().notExists()
      }
      .repeat(_.in(ModifiedBy))
      .out(AuthoredBy)
  }

  def getRootRevision(v: Vertex) = {
    v.graph.V(v.id)
      .untilWithTraverser(t => t.get().in(DescribedBy).exists)
      .repeat(_.in(ModifiedBy))
      .in(DescribedBy)
  }


  implicit class GremlinScalaImplicits(gs: GremlinScala[Vertex, _]) {
    def canonicalOption: Option[Canonical] = {
      gs.flatMap(getCanonical)
        .toCC[Canonical]
        .headOption
    }

    def authorOption: Option[Canonical] = {
      gs.flatMap(getAuthor)
        .toCC[Canonical]
        .headOption
    }
  }
}
