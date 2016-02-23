package org.mediachain

import org.mediachain.Types._

object Query {
  import gremlin.scala._

  /** Finds a vertex with label "Person" and traits matching `p` in the graph
    * `g`.
    *
    * @param graph The OrientGraph object
    * @param p The person to search for
    * @return Optional person matching criteria
    */
  def findPerson(graph: Graph, p: Person): Option[Canonical] = {
    val Name = Key[String]("name")

    // at some point, this should prob search for inbound edge of HEAD or do
    // something to promote fielding multiple valid-ish results
    graph.V
      .hasLabel[Person]
      .has(Name, p.name)
      .in(DescribedBy)
      .headOption
      .map(_.toCC[Canonical])
  }

  def findPhotoBlob(graph: Graph, p: PhotoBlob): Option[Canonical] = {
    val Title = Key[String]("title")
    val Description = Key[String]("description")
    val Date = Key[String]("date")

    // TODO(bigs): simplify this `has` stuff with HList
    graph.V
      .hasLabel[PhotoBlob]
      .has(Title, p.title)
      .has(Description, p.description)
      .has(Date, p.date)
      .in(DescribedBy)
      .headOption
      .map(_.toCC[Canonical])
  }

  def rootRevisionVertexForBlob[T <: MetadataBlob](graph: Graph, blob: T): Option[Vertex] = {
    blob.id.flatMap { id =>
      graph.V(id)
        .repeat(_.in(ModifiedBy))
        .untilWithTraverser(t => t.get().in(DescribedBy).exists)
        .headOption
    }
  }

  def findCanonicalForBlob(graph: Graph, blobID: String): Option[Canonical] = {
    graph.V(blobID)
      .repeat(_.in(ModifiedBy))
      .untilWithTraverser(t => t.get().in(DescribedBy).exists)
      .in(DescribedBy)
      .headOption()
      .map(_.toCC[Canonical])
  }

  def findCanonicalForBlob[T <: MetadataBlob](graph: Graph, blob: T): Option[Canonical] = {
    blob.id.flatMap(id => findCanonicalForBlob(graph, id))
  }

  def findAuthorForBlob[T <: MetadataBlob](graph: Graph, blob: T):
  Option[Canonical] = {
    blob.id.flatMap { id =>
      graph.V(id)
        .repeat(_.in(ModifiedBy))
        .untilWithTraverser { t =>
          t.get().in(AuthoredBy).exists() || t.get().in().notExists()
        }
        .in(AuthoredBy)
        .headOption()
    }.map(_.toCC[Canonical])
  }
}

