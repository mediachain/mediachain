package org.mediachain

import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.mediachain.Types._
import scala.util.Try
import gremlin.scala._

object Ingress {
  // throws?
  def addPerson(graph: Graph, author: Person): Canonical = {
    val canonicalVertex = graph + Canonical.create
    val personVertex = graph + author

    canonicalVertex --- DescribedBy --> personVertex

    Canonical(canonicalVertex)
  }

  def addPhotoBlob(graph: OrientGraph, photo: PhotoBlob): Canonical = {
    // 1) extract author & see if they exist already
    val author: Option[Canonical] = photo.author match {
      case Some(p) =>
        Query.findPerson(graph, p).orElse {
          Some(addPerson(graph, p))
        }
      case _       => None
    }

    // 2) check to see if a duplicate entry exists
    Query.findPhotoBlob(graph, photo).getOrElse {
      val canonicalVertex = graph + Canonical.create
      val photoVertex = graph + photo

      canonicalVertex --- DescribedBy --> photoVertex

      Canonical(canonicalVertex)
    }
  }
}

