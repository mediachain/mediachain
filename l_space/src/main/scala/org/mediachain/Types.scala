package org.mediachain

object Types {
  import gremlin.scala._
  import java.util.UUID

  val DescribedBy = "described-by"
  val AuthoredBy  = "authored-by"

  @label("Canonical")
  case class Canonical(@id id: Option[String],
                       canonicalID: String)

  object Canonical {
    def create(): Canonical = {
      Canonical(None, UUID.randomUUID.toString)
    }

    def apply(v: Vertex): Canonical = {
      Canonical(
        v.valueOption[String]("id"),
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
            id = v.valueOption[String]("id"),
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
            id = v.valueOption[String]("id"),
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

