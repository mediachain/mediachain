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

import java.security.PrivateKey

import com.orientechnologies.orient.core.id.ORecordId
import io.mediachain.signatures.{Signatory, Signer}
import io.mediachain.util.MultiHash
import org.json4s.FieldSerializer
import org.json4s.FieldSerializer.ignore

import scala.collection.JavaConverters._
import java.util.{Map => JMap}

import com.orientechnologies.orient.core.exception.OStorageException


object Types {
  import java.util.UUID

  import cats.data.Xor
  import gremlin.scala._
  import io.mediachain.core.GraphError._

  type ElementID = ORecordId

  val DescribedBy = "described-by"
  val ModifiedBy  = "modified-by"
  val AuthoredBy  = "authored-by"
  val TranslatedFrom = "translated-from"
  val SupersededBy = "superseded-by"

  object Keys {
    val Deprecated = Key[Boolean]("deprecated")
    val MultiHash = Key[String]("multiHash")
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


  def vertexToMetadataBlob(vertex: Vertex): Option[MetadataBlob] =
    vertex.label match {
      case ImageBlob.VertexLabel => Some(vertex.toCC[ImageBlob])
      case Person.VertexLabel => Some(vertex.toCC[Person])
      case RawMetadataBlob.VertexLabel => Some(vertex.toCC[RawMetadataBlob])
      case _ => None
    }


  trait Hashable {
    def multiHash: MultiHash =
      MultiHash.forHashable(this)

    // When computing the hash of an object, we want to ignore the
    // internal graph id, but we do want to include any signatures
    // attached to the object.
    def hashSerializer: FieldSerializer[this.type] =
      FieldSerializer[this.type](ignore("id"))
  }

  object Hashable {

    def convertMapsToJava(valueMap: Map[String, Any]): Map[String, Any] =
      valueMap.map { entry =>
        val (key: String, value: Any) = entry
        value match {
          case mapVal: Map[_, _] => (key, mapVal.asJava)
          case _ => (key, value)
        }
      }

    def convertMapsToScala(valueMap: Map[String, Any]): Map[String, Any] =
      valueMap.map { entry =>
        val (key: String, value: Any) = entry
        value match {
          case mapVal: java.util.Map[_, _] => (key, mapVal.asScala.toMap)
          case _ => (key, value)
        }
      }

    // Extend the default `Marshallable` implementation for `Hashable` case classes
    // to include the computed `multiHash` as a vertex property.
    //
    // The default marshaller will only store the constructor parameters for a case class.
    // Since the hash is a computed property, it doesn't get stored by default.
    // This method creates a new `Marshallable` that will include the `multiHash`

    def marshaller[CC <: Hashable with Product : Marshallable]: Marshallable[CC] = {
      new Marshallable[CC] {
        override def fromCC(cc: CC): FromCC = {
          val defaultFromCC = implicitly[Marshallable[CC]].fromCC(cc)

          val valueMap = convertMapsToJava(
            defaultFromCC.valueMap + ("multiHash" -> cc.multiHash.base58))


          FromCC(defaultFromCC.id, defaultFromCC.label, valueMap)
        }

        override def toCC(id: Id, valueMap: ValueMap): CC =
          implicitly[Marshallable[CC]]
            .toCC(id, convertMapsToScala(valueMap) + ("id" -> id))
      }
    }
    def serializer: FieldSerializer[this.type] =
      FieldSerializer(ignore("id"))
  }

  implicit val canonicalMarshaller = Hashable.marshaller[Canonical]
  implicit val rawMetadataBlobMarshaller = Hashable.marshaller[RawMetadataBlob]
  implicit val imageBlobMarshaller = Hashable.marshaller[ImageBlob]
  implicit val personMarshaller = Hashable.marshaller[Person]


  // mapping of form "signer" -> "signature"
  type SignatureMap = Map[String, String]
  // mapping of form "namespace:externalKeyName" -> "externalValue"
  type IdMap = Map[String, String]

  trait Signable {
    val signatures: SignatureMap

    // When signing, we need to ignore both the id and any existing
    // signatures.
    // the `ignore` partial functions are chained with `orElse`, so if
    // first `ignore` is not defined for a field, it will try the second.
    def signingSerializer: FieldSerializer[this.type] =
      FieldSerializer[this.type](
        {
          case ("id", _) => None
          case ("signatures", _) => None
        }
      )

    def signature(privateKey: PrivateKey): String = {
      Signer.signatureForSignable(this, privateKey)
    }

    def withSignature(signingIdentity: String, privateKey: PrivateKey)
    : this.type

    def withSignature(signatory: Signatory): this.type =
      withSignature(signatory.commonName, signatory.privateKey)
  }


  trait VertexClass extends Hashable {
    def getID(): Option[ElementID]

    def vertex(graph: Graph): Xor[VertexNotFound, Vertex] = {
      for {
        id <- Xor.fromOption(getID(), VertexNotFound())
        vertexOption <- Xor.catchOnly[OStorageException](graph.V(id).headOption)
          .leftMap(_ => VertexNotFound())
          .flatMap(Xor.fromOption(_, VertexNotFound()))
      } yield { vertexOption }
    }

    def query(graph: Graph): Xor[VertexNotFound, GremlinScala[Vertex, _]] = {
      for {
        id <- Xor.fromOption(getID(), VertexNotFound())
      } yield { graph.V(id) }
    }
  }

  @label(Canonical.VertexLabel)
  case class Canonical(
    @id id: Option[ElementID],
    canonicalID: String,
    signatures: SignatureMap = Map()
  ) extends VertexClass with Signable {
    def getID(): Option[ElementID] = id

    def withSignature(signingIdentity: String, privateKey: PrivateKey)
    : this.type = {
      this.copy(signatures = signatures +
        (signingIdentity -> this.signature(privateKey)))
        .asInstanceOf[this.type]
    }
  }

  object Canonical {
    val VertexLabel = "Canonical"
    object Keys {
      val canonicalID = Key[String]("canonicalID")
    }

    def create(): Canonical = {
      Canonical(None, UUID.randomUUID.toString)
    }
  }

  sealed trait MetadataBlob extends VertexClass with Signable

  @label(RawMetadataBlob.VertexLabel)
  case class RawMetadataBlob(
    @id id: Option[ElementID],
    blob: String,
    signatures: SignatureMap = Map()
  ) extends MetadataBlob {
    def getID(): Option[ElementID] = id

    def withSignature(signingIdentity: String, privateKey: PrivateKey)
    : this.type =
      this.copy(signatures = signatures +
        (signingIdentity -> this.signature(privateKey)))
        .asInstanceOf[this.type]
  }

  object RawMetadataBlob {
    val VertexLabel = "RawMetadataBlob"
    object Keys {
      val blob = Key[String]("blob")
    }
  }

  @label(Person.VertexLabel)
  case class Person(
    @id id: Option[ElementID],
    name: String,
    signatures: SignatureMap = Map(),
    external_ids: IdMap = Map()
  ) extends MetadataBlob {
    def getID(): Option[ElementID] = id

    def withSignature(signerCommonName: String, privateKey: PrivateKey)
    : this.type =
      this.copy(signatures = signatures +
        (signerCommonName -> this.signature(privateKey)))
        .asInstanceOf[this.type]
  }

  object Person {
    val VertexLabel = "Person"
    object Keys {
      val name = Key[String]("name")
    }

    def create(name: String) = {
      Person(None, name)
    }
  }

  @label(ImageBlob.VertexLabel)
  case class ImageBlob(
    @id id: Option[ElementID],
    title: String,
    description: String,
    date: String,
    signatures: SignatureMap = Map(),
    external_ids: IdMap = Map()
  ) extends MetadataBlob {


    def getID(): Option[ElementID] = id

    def withSignature(signingIdentity: String, privateKey: PrivateKey)
    : this.type =
      this.copy(signatures = signatures +
        (signingIdentity -> this.signature(privateKey)))
        .asInstanceOf[this.type]
  }

  object ImageBlob {
    val VertexLabel = "ImageBlob"
    object Keys {
      val title = Key[String]("title")
      val description = Key[String]("description")
      val date = Key[String]("date")
      val external_ids = Key[JMap[String, String]]("external_ids")
    }
  }
}

