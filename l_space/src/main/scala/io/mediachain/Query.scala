package io.mediachain

import io.mediachain.Types._

object Query {
  import gremlin.scala._
  import Traversals.GremlinScalaImplicits
  import core.GraphError
  import core.GraphError._
  import cats.data.Xor

  /** Finds a vertex with label "Person" and traits matching `p` in the graph
    * `g`.
    *
    * @param graph The OrientGraph object
    * @param p The person to search for
    * @return Optional person matching criteria
    */
  def findPerson(graph: Graph, p: Person): Xor[CanonicalNotFound, Canonical] = {
    Traversals.personBlobsWithExactMatch(graph.V, p)
      .findCanonicalXor
  }

  def findPhotoBlob(graph: Graph, p: PhotoBlob):
  Xor[CanonicalNotFound, Canonical] = {
    Traversals.photoBlobsWithExactMatch(graph.V, p)
      .findCanonicalXor
  }

  def findCanonicalForBlob(graph: Graph, blobID: ElementID):
  Xor[CanonicalNotFound, Canonical] = {
    graph.V(blobID).findCanonicalXor
  }

  def findCanonicalForBlob[T <: MetadataBlob](graph: Graph, blob: T):
  Xor[CanonicalNotFound, Canonical] = {
    blob.getID().map(findCanonicalForBlob(graph, _))
      .getOrElse(Xor.left(CanonicalNotFound()))
  }

  def findCanonicalForBlob(graph: Graph, vertex: Vertex):
  Xor[CanonicalNotFound, Canonical] = {
    vertexId(vertex).map(findCanonicalForBlob(graph, _))
      .getOrElse(Xor.left(CanonicalNotFound()))
  }

  def findAuthorForBlob[T <: MetadataBlob](graph: Graph, blob: T):
  Xor[CanonicalNotFound, Canonical] = {
    blob.getID.map(id => graph.V(id).findAuthorXor)
        .getOrElse(Xor.left(CanonicalNotFound()))
  }

  def findTreeForCanonical[T <: Canonical](graph: Graph, canonical: Canonical): Xor[GraphError, Graph] = {
    canonical.getID.map(id => graph.V(id).findSubtreeXor)
      .getOrElse(Xor.left(CanonicalNotFound()))
  }

  def findWorks(graph: Graph, p: Person):
  Xor[GraphError, List[Canonical]] = {
    for {
      personCanonical <- findCanonicalForBlob(graph, p)
      vertex          <- personCanonical.vertex(graph)
      items = vertex.in(AuthoredBy)
        .map(v => findCanonicalForBlob(graph, v))
        .toList
    } yield { items.flatMap(_.toList) }
  }
}

