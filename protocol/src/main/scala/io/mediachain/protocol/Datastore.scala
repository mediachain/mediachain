package io.mediachain.protocol

object Datastore {
  import io.mediachain.multihash.MultiHash
  import io.mediachain.protocol.CborSerialization._
  import io.mediachain.protocol.Transactor._
  import io.mediachain.util.cbor.CborAST._
  import scala.util.Try
  import dogs.Streaming
  import cats.Eval

  /** Datastore interface.
    *
    * Implementations of this must be thread-safe
    *
    */
  trait Datastore {
    def get(ref: Reference): Option[DataObject]
    def put(obj: DataObject): Reference

    def getAs[T <: DataObject](ref: Reference): Option[T] =
      get(ref).flatMap(obj => Try(obj.asInstanceOf[T]).toOption)
  }

  class DatastoreException(what: String) extends RuntimeException(what)

  // Base class of all objects storable in the Datastore
  sealed trait DataObject extends Serializable with CborSerializable

  // Mediachain Datastore Records
  sealed abstract class Record extends DataObject {
    def meta: Map[String, CValue]
    def metaSource: Option[Reference]
  }


  // References to records in the underlying datastore
  sealed abstract class Reference extends Serializable with CborSerializable

  // Content-addressable reference using IPFS MultiHash
  case class MultihashReference(multihash: MultiHash) extends Reference {
    override val mediachainType: Option[MediachainType] = None

    override def toCbor: CValue =
      CMap.withStringKeys("@link" -> CBytes(multihash.bytes))
  }

  object MultihashReference {
    def forDataObject(dataObject: DataObject): MultihashReference =
      MultihashReference(
        MultiHash.hashWithSHA256(dataObject.toCborBytes)
      )
  }
  
  // DummyReferences are used for testing but still need to be CBOR serializable
  case class DummyReference(index: Int) extends Reference {
    override val mediachainType: Option[MediachainType] = None
    override def toCbor: CValue =
      CMap.withStringKeys("@dummy" -> CInt(index))
  }

  // Canonical records: Entities and Artefacts
  sealed abstract class CanonicalRecord extends Record {
    def reference: ChainReference
  }

  case class Entity(
    meta: Map[String, CValue],
    metaSource: Option[Reference] = None
  ) extends CanonicalRecord {
    override val mediachainType: Option[MediachainType] = 
      Some(MediachainTypes.Entity)

    override def toCbor =
      super.toCMapWithMeta(Map(), Map(), meta, metaSource)

    def reference: ChainReference = EntityChainReference.empty
  }

  case class Artefact(
    meta: Map[String, CValue],
    metaSource: Option[Reference] = None
  ) extends CanonicalRecord {
    override val mediachainType: Option[MediachainType] = 
      Some(MediachainTypes.Artefact)

    override def toCbor =
      super.toCMapWithMeta(Map(), Map(), meta, metaSource)

    def reference: ChainReference = ArtefactChainReference.empty
  }


  // Chain cells
  sealed abstract class ChainCell extends Record {
    def ref: Reference
    def chain: Option[Reference]
    def cons(chain: Option[Reference]): ChainCell
    
    override def toCbor = {
      val defaults = Map("ref" -> ref.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }
  }

  class EntityChainCell(
    val entity: Reference,
    val chain: Option[Reference],
    val meta: Map[String, CValue],
    val metaSource: Option[Reference]
  ) extends ChainCell {
    val ref = entity

    override def cons(xchain: Option[Reference]): ChainCell =
      EntityChainCell(entity, xchain, meta, metaSource)

    override val mediachainType: Option[MediachainType] = 
      Some(MediachainTypes.EntityChainCell)
  }

  object EntityChainCell {
    def apply(entity: Reference, 
              chain: Option[Reference], 
              meta: Map[String, CValue], 
              metaSource: Option[Reference] = None)
    = new EntityChainCell(entity, chain, meta, metaSource)

    def unapply(cell: EntityChainCell)
    : Option[(Reference, Option[Reference], Map[String, CValue], Option[Reference])] =
      Some((cell.entity, cell.chain, cell.meta, cell.metaSource))
  }

  class ArtefactChainCell(
    val artefact: Reference,
    val chain: Option[Reference],
    val meta: Map[String, CValue],
    val metaSource: Option[Reference]
  ) extends ChainCell {
    val ref = artefact

    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactChainCell(artefact, xchain, meta, metaSource)
    
    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactChainCell)
  }

  object ArtefactChainCell {
    def apply(artefact: Reference, 
              chain: Option[Reference], 
              meta: Map[String, CValue], 
              metaSource: Option[Reference] = None)
    = new ArtefactChainCell(artefact, chain, meta, metaSource)

    def unapply(cell: ArtefactChainCell)
    : Option[(Reference, Option[Reference], Map[String, CValue], Option[Reference])] =
      Some((cell.artefact, cell.chain, cell.meta, cell.metaSource))
  }


  // Entity Chain cell subtypes
  case class EntityUpdateCell(
    override val entity: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String,CValue],
    override val metaSource: Option[Reference]
  ) extends EntityChainCell(entity, chain, meta, metaSource) {
    override def cons(xchain: Option[Reference]): ChainCell =
      EntityUpdateCell(entity, xchain, meta, metaSource)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.EntityUpdateCell)
  }

  case class EntityLinkCell(
    override val entity: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference],
    entityLink: Reference
  ) extends EntityChainCell(entity, chain, meta, metaSource)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      EntityLinkCell(entity, xchain, meta, metaSource, entityLink)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.EntityLinkCell)

    override def toCbor = {
      val defaults = Map("ref" -> entity.toCbor,
                         "entityLink" -> entityLink.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }
  }


  // Artefact chain cell subtypes
  case class ArtefactUpdateCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference]
  ) extends ArtefactChainCell(artefact, chain, meta, metaSource) {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactUpdateCell(artefact, xchain, meta, metaSource)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactUpdateCell)
  }

  case class ArtefactLinkCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference],
    artefactLink: Reference
  ) extends ArtefactChainCell(artefact, chain, meta, metaSource) {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactLinkCell(artefact, xchain, meta, metaSource, artefactLink)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactLinkCell)
    
    override def toCbor = {
      val defaults = Map("ref" -> artefact.toCbor,
                         "artefactLink" -> artefactLink.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }

  }

  case class ArtefactCreationCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference],
    entity: Reference
  ) extends ArtefactChainCell(artefact, chain, meta, metaSource)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactCreationCell(artefact, xchain, meta, metaSource, entity)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactCreationCell)

    override def toCbor = {
      val defaults = Map("ref" -> artefact.toCbor,
                         "entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }
  }

  case class ArtefactDerivationCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference],
    artefactLink: Reference
  ) extends ArtefactChainCell(artefact, chain, meta, metaSource)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactDerivationCell(artefact, xchain, meta, metaSource, artefactLink)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactDerivationCell)

    override def toCbor = {
      val defaults = Map("ref" -> artefact.toCbor,
                         "artefactLink" -> artefactLink.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }
  }

  case class ArtefactOwnershipCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference],
    entity: Reference
  ) extends ArtefactChainCell(artefact, chain, meta, metaSource)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactOwnershipCell(artefact, xchain, meta, metaSource, entity)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactOwnershipCell)

    override def toCbor = {
      val defaults = Map("ref" -> artefact.toCbor,
                         "entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }
  }

  case class ArtefactReferenceCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    override val metaSource: Option[Reference],
    entity: Option[Reference]
  ) extends ArtefactChainCell(artefact, chain, meta, metaSource)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactReferenceCell(artefact, xchain, meta, metaSource, entity)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactReferenceCell)

    override def toCbor = {
      val defaults = Map("ref" -> artefact.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor),
                          "entity" -> entity.map(_.toCbor))
      super.toCMapWithMeta(defaults, optionals, meta, metaSource)
    }
  }

  // Journal Entries
  sealed abstract class JournalEntry extends Serializable with CborSerializable {
    def index: BigInt
    def ref: Reference
  }

  case class CanonicalEntry(
    index: BigInt,
    ref: Reference
  ) extends JournalEntry {
    override val mediachainType: Option[MediachainType] = 
      Some(MediachainTypes.CanonicalEntry)

    override def toCbor: CValue = {
      val defaults = Map(
        "index" -> CInt(index),
        "ref"   -> ref.toCbor
      )
      super.toCMapWithDefaults(defaults, Map())
    }
  }


  case class ChainEntry(
    index: BigInt,
    ref: Reference,
    chain: Reference,
    chainPrevious: Option[Reference]
  ) extends JournalEntry {
    override val mediachainType: Option[MediachainType] = 
      Some(MediachainTypes.ChainEntry)

    override def toCbor: CValue = {
      val defaults = Map(
        "index" -> CInt(index),
        "ref"   -> ref.toCbor,
        "chain" -> chain.toCbor
      )
      val prev = chainPrevious.map(_.toCbor)
      val optionals = Map("chainPrevious" -> prev)

      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  // Journal Blocks
  case class JournalBlock(
    index: BigInt,
    chain: Option[Reference],
    entries: Array[JournalEntry]
  ) extends DataObject {
    override val mediachainType: Option[MediachainType] = 
      Some(MediachainTypes.JournalBlock)

    override def toCbor = {
      val cborEntries = CArray(entries.map(_.toCbor).toList)
      val defaults = Map("index" -> CInt(index), "entries" -> cborEntries)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }
  
  // Fat Journal Blocks for Archival purposes
  case class JournalBlockArchive(
    ref: Reference,
    block: JournalBlock,
    data: Map[Reference, Array[Byte]]
  ) extends DataObject {
    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.JournalBlockArchive)
    
    override def toCbor = {
      val xdata = data.toList.map { 
        case (ref, bytes) => CArray(List(ref.toCbor, CBytes(bytes)))
      }
      val defaults = Map("ref" -> ref.toCbor,
                         "block" -> block.toCbor,
                         "data" -> CArray(xdata))
      super.toCMapWithDefaults(defaults, Map())
    }
  }
}
