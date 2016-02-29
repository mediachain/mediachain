package org.mediachain

import org.mediachain.Types._
import gremlin.scala._

object Ingress {
  // throws?
  def addPerson(graph: Graph, author: Person): Canonical = {
    // If there's an exact match already, return it,
    // otherwise create a new Person vertex and canonial
    // and return the canonical
    Query.findPerson(graph, author)
      .flatMap(Query.findCanonicalForBlob(graph, _))
      .getOrElse {
        val canonicalVertex = graph + Canonical.create
        val personVertex = graph + author

        canonicalVertex --- DescribedBy --> personVertex

        Canonical(canonicalVertex)
      }
  }

  def addPhotoBlob(graph: Graph, photo: PhotoBlob): Canonical = {
    // 1) extract author & add if they don't exist in the graph already
    val author: Option[Canonical] = photo.author.map { p =>
      addPerson(graph, p)
    }

    // 2) check to see if a duplicate entry exists
    Query.findPhotoBlob(graph, photo)
      .flatMap(Query.findCanonicalForBlob(graph, _))
      .getOrElse {
        val canonicalVertex = graph + Canonical.create
        val photoVertex = graph + photo

        canonicalVertex --- DescribedBy --> photoVertex

        // 3) when adding a new entry with an author,
        //    make an edge from the PhotoBlob vertex to
        //    the Canonical vertex for the author
        author
          .flatMap(a => a.vertex(graph))
          .foreach({authorVertex => photoVertex --- AuthoredBy --> authorVertex})

        Canonical(canonicalVertex)
      }
  }


  def modifyPhotoBlob(graph: Graph, parentVertex: Vertex, photo: PhotoBlob): Option[Canonical] = {
    Query.findPhotoBlob(graph, photo)
      .flatMap(Query.findCanonicalForBlob(graph, _))
      .orElse {
        val childVertex = graph + photo
        parentVertex --- ModifiedBy --> childVertex

        val author: Option[Canonical] = photo.author.map { p =>
          addPerson(graph, p)
        }

        (author, Query.findAuthorForBlob(graph, photo)) match {
          case (Some(newAuthor), Some(oldAuthor)) => {
            if (newAuthor.canonicalID != oldAuthor.canonicalID) {
              newAuthor.vertex(graph).foreach(v => {
                childVertex --- AuthoredBy --> v
              })
            }
          }
          case _ => ()
        }

        Query.findCanonicalForBlob(graph, childVertex)
      }
  }


  def modifyPhotoBlob(graph: Graph, parentID: String, photo: PhotoBlob): Option[Canonical] = {
    graph.V(parentID).headOption
      .flatMap(v => modifyPhotoBlob(graph, v, photo))
  }

}

