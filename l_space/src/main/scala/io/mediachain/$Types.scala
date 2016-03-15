/**
  * Please note that the odd naming of this file is intentional!
  * Because we're relying on macro magic (via gremlin-scala), this file needs
  * to be compiled before any file that uses the `toCC` or `fromCC` marshalling
  * calls with the types defined in this file.
  *
  * sbt compiles files in alphabetical order, so this file is named `$Types.scala`,
  * since the `$` character sorts before any alphanumeric char.
  *
  * Fun!
  *
  */


package io.mediachain

import io.mediachain.util.{ParsingError, MultiHash}
import com.orientechnologies.orient.core.id.ORecordId
import ParsingError.ConversionToJsonFailed

object Types {
  import gremlin.scala._
  import java.util.UUID
  import cats.data.Xor
  import io.mediachain.core.GraphError
  import io.mediachain.core.GraphError._


  type ElementID = ORecordId

  val DescribedBy = "described-by"
  val ModifiedBy  = "modified-by"
  val AuthoredBy  = "authored-by"
  val TranslatedFrom = "translated-from"
  val SupersededBy = "superseded-by"

  object Keys {
    val Deprecated = Key[Boolean]("deprecated")
  }


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

  trait Hashable {
    val excludedFields: List[String] = List("id")

    def multiHash: Xor[ConversionToJsonFailed, MultiHash] =
      MultiHash.forHashable(this)
  }

  trait VertexClass extends Hashable {
    def getID(): Option[ElementID]

    def vertex(graph: Graph): Xor[VertexNotFound, Vertex] = {
      for {
        id <- Xor.fromOption(getID(), VertexNotFound())
        vertexOption <- Xor.fromOption(graph.V(id).headOption(),
          VertexNotFound())
      } yield { vertexOption }
    }

    def query(graph: Graph): Xor[VertexNotFound, GremlinScala[Vertex, _]] = {
      for {
        id <- Xor.fromOption(getID(), VertexNotFound())
      } yield { graph.V(id) }
    }
  }

  @label("Canonical")
  case class Canonical(@id id: Option[ElementID],
                       canonicalID: String) extends VertexClass {
    def getID(): Option[ElementID] = id
  }

  object Canonical {
    object Keys {
      val canonicalID = Key[String]("canonicalID")
    }

    def create(): Canonical = {
      Canonical(None, UUID.randomUUID.toString)
    }
  }

  sealed trait MetadataBlob extends VertexClass

  @label("RawMetadataBlob")
  case class RawMetadataBlob(@id id: Option[ElementID],
                             blob: String) extends MetadataBlob {
    def getID(): Option[ElementID] = id
  }

  object RawMetadataBlob {
    object Keys {
      val blob = Key[String]("blob")
    }
  }

  @label("Person")
  case class Person(@id id: Option[ElementID],
                    name: String) extends MetadataBlob {
    def getID(): Option[ElementID] = id
  }

  object Person {
    object Keys {
      val name = Key[String]("name")
    }

    def create(name: String) = {
      Person(None, name)
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
    object Keys {
      val title = Key[String]("title")
      val description = Key[String]("description")
      val date = Key[String]("date")
    }
  }
}

