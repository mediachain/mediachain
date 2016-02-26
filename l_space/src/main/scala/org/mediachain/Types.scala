package org.mediachain

import com.orientechnologies.orient.core.id.ORecordId

object Types {
  import gremlin.scala._
  import java.util.UUID

  type ElementID = ORecordId

  val DescribedBy = "described-by"
  val ModifiedBy  = "modified-by"
  val AuthoredBy  = "authored-by"

  /**
    * Convert from the AnyRef returned by Vertex.id()
    * to an Option[ElementID]
 *
    * @param v a Vertex
    * @return Some(ElementID), or None if no id exists
    */
  def vertexId(v: Vertex): Option[ElementID] = {
    Option(v.id).map(id => id.asInstanceOf[ElementID])
  }

  trait VertexClass {
    def getID(): Option[ElementID]

    def vertex(graph: Graph): Option[Vertex] = {
      val id = getID().getOrElse(throw new Exception("Malformed vertex object: no id"))
      graph.V(id).headOption()
    }
  }

  @label("Canonical")
  case class Canonical(@id id: Option[ElementID],
                       canonicalID: String) extends VertexClass {
    def getID(): Option[ElementID] = id
  }

  object Canonical {
    def create(): Canonical = {
      Canonical(None, UUID.randomUUID.toString)
    }

    def apply(v: Vertex): Canonical = {
      Canonical(
        vertexId(v),
        v.value[String]("canonicalID")
      )
    }
  }

  sealed trait MetadataBlob extends VertexClass

  @label("RawMetadataBlob")
  case class RawMetadataBlob(@id id: Option[ElementID],
                             blob: String) extends MetadataBlob {
    def getID(): Option[ElementID] = id
  }

  @label("Person")
  case class Person(@id id: Option[ElementID],
                    name: String) extends MetadataBlob {
    def getID(): Option[ElementID] = id
  }

  object Person {
    def create(name: String) = {
      Person(None, name)
    }

    def apply(v: Vertex): Option[Person] = {
      if (v.label() == "Person") {
        Some(
          Person(
            vertexId(v),
            name = v.value("name")
          )
        )
      } else {
        None
      }
    }
  }

  @label("PhotoBlob")
  case class PhotoBlob(@id id: Option[ElementID],
                       title: String,
                       description: String,
                       date: String,
                       author: Option[Person]) extends MetadataBlob {
    def getID(): Option[ElementID] = id
  }

  object PhotoBlob {
    def apply(v: Vertex): Option[PhotoBlob] = {
      if (v.label() == "PhotoBlob") {
        Some(
          PhotoBlob(
            vertexId(v),
            title = v.value("title"),
            description = v.value("description"),
            date = v.value("date"),
            author = None // FIXME(yusef)
          )
        )
      } else {
        None
      }
    }
  }
}

