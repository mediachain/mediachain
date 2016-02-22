package org.mediachain

import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
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
  def findPerson(graph: OrientGraph, p: Person): Option[Canonical] = {
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

  def findPhotoBlob(graph: OrientGraph, p: PhotoBlob): Option[PhotoBlob] = {
    val Title = Key[String]("title")
    val Description = Key[String]("description")
    val Date = Key[String]("date")

    graph.V
      .hasLabel[PhotoBlob]
      .has(Title, p.title)
      .has(Description, p.description)
      .has(Date, p.date)
      .headOption
      .map(_.toCC[PhotoBlob])
  }
}

