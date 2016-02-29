package org.mediachain

import org.mediachain.Types._
import gremlin.scala._

object Ingress {
  import Traversals.GremlinScalaImplicits

  def attachRawMetadata(blobV: Vertex, raw: RawMetadataBlob): Unit = {
    val graph = blobV.graph

    // add the raw metadata to the graph if it doesn't already exist
    val rawV = Traversals.rawMetadataWithExactMatch(graph.V, raw)
      .headOption
      .getOrElse(graph + raw)

    val edgeExists = Traversals.getRawMetadataForBlob(blobV)
      .toSet
      .contains(rawV)

    if (!edgeExists) {
      blobV --- TranslatedFrom --> rawV
    }
  }

  // throws?
  def addPerson(graph: Graph, author: Person, raw: Option[RawMetadataBlob] = None): Canonical = {
    // If there's an exact match already, return it,
    // otherwise create a new Person vertex and canonial
    // and return the canonical
    val q = Traversals.personWithExactMatch(graph.V, author)

    val personV: Vertex = q.headOption.getOrElse(graph + author)
    raw.foreach(attachRawMetadata(personV, _))

    graph.V(personV.id)
      .canonicalOption
      .getOrElse {
        val canonicalV = graph + Canonical.create()
        canonicalV --- DescribedBy --> personV
        canonicalV.toCC[Canonical]
      }
  }

  def addPhotoBlob(graph: Graph, photo: PhotoBlob, raw: Option[RawMetadataBlob] = None): Canonical = {
    // extract author & add if they don't exist in the graph already
    val author: Option[Canonical] = photo.author.map { p =>
      addPerson(graph, p, raw)
    }

    // check to see if a duplicate entry exists
    val photoV = Traversals.photoBlobWithExactMatch(graph.V, photo)
        .headOption.getOrElse(graph + photo)

    // attach raw metadata (if it exists) to photo vertex
    raw.foreach(attachRawMetadata(photoV, _))

    // return existing canonical for photo vertex, or create one
    graph.V(photoV.id)
      .canonicalOption
      .getOrElse {
        val canonicalVertex = graph + Canonical.create
        canonicalVertex --- DescribedBy --> photoV

        //    when adding a new entry with an author,
        //    make an edge from the PhotoBlob vertex to
        //    the Canonical vertex for the author
        //  TODO: pull this out into an idempotent method, call outside of this getOrElse block
        author
          .flatMap(a => a.vertex(graph))
          .foreach({authorVertex => photoV --- AuthoredBy --> authorVertex})

        Canonical(canonicalVertex)
      }
  }


  def modifyPhotoBlob(graph: Graph, parentVertex: Vertex, photo: PhotoBlob): Option[Canonical] = {
    Query.findPhotoBlob(graph, photo)
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

