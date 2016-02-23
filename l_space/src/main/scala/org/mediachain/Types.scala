package org.mediachain

object Types {
  import gremlin.scala._
  import java.util.UUID

  val DescribedBy = "described-by"
  val AuthoredBy  = "authored-by"

  /**
    * Convert from the AnyRef returned by Vertex.id()
    * to an Option[String]
    * @param v a Vertex
    * @return Some("StringId"), or None if no id exists
    */
  def vertexId(v: Vertex): Option[String] = {
    Option(v.id).map(_.toString)
  }

  @label("Canonical")
  case class Canonical(@id id: Option[String],
                       canonicalID: String)

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

  sealed trait MetadataBlob

  @label("RawMetadataBlob")
  case class RawMetadataBlob(@id id: String,
                             blob: String) extends MetadataBlob

  @label("Person")
  case class Person(@id id: Option[String],
                    name: String) extends MetadataBlob

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
  case class PhotoBlob(@id id: Option[String],
                       title: String,
                       description: String,
                       date: String,
                       author: Option[Person]) extends MetadataBlob

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

