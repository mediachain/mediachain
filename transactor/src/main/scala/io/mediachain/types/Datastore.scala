package io.mediachain.types



object Datastore {
  import io.mediachain.multihash.MultiHash
  import io.mediachain.types.CborSerialization._
  import io.mediachain.types.Transactor.{ArtefactChainReference, ChainReference, EntityChainReference}
  import io.mediachain.util.cbor.CborAST._

  // Datastore interface
  trait Datastore {
    def get(ref: Reference): Option[DataObject]
    def put(obj: DataObject): Reference
  }

  // Base class of all objects storable in the Datastore
  sealed trait DataObject extends Serializable with CborSerializable

  // Mediachain Datastore Records
  sealed abstract class Record extends DataObject {
    def meta: Map[String, CValue]
  }


  // References to records in the underlying datastore
  abstract class Reference extends Serializable with CborSerializable

  // Content-addressable reference using IPFS MultiHash
  case class MultihashReference(multihash: MultiHash) extends Reference {
    val mediachainType: Option[MediachainType] = None

    override def toCbor: CValue =
      CMap.withStringKeys("@link" -> CBytes(multihash.bytes))
  }


  // Canonical records: Entities and Artefacts
  sealed abstract class CanonicalRecord extends Record {
    def reference: ChainReference
  }

  case class Entity(
    meta: Map[String, CValue]
  ) extends CanonicalRecord {
    val mediachainType: Option[MediachainType] = Some(MediachainTypes.Entity)

    override def toCbor =
      super.toCMapWithDefaults(meta, Map())

    def reference: ChainReference = EntityChainReference.empty
  }

  case class Artefact(
    meta: Map[String, CValue]
  ) extends CanonicalRecord {
    val mediachainType: Option[MediachainType] = Some(MediachainTypes.Artefact)

    override def toCbor =
      super.toCMapWithDefaults(meta, Map())

    def reference: ChainReference = ArtefactChainReference.empty
  }


  // Chain cells
  sealed abstract class ChainCell extends Record {
    def chain: Option[Reference]
    def cons(chain: Option[Reference]): ChainCell
  }

  class EntityChainCell(
    val entity: Reference,
    val chain: Option[Reference],
    val meta: Map[String, CValue]
  ) extends ChainCell {
    override def cons(xchain: Option[Reference]): ChainCell =
      EntityChainCell(entity, xchain, meta)

    val mediachainType: Option[MediachainType] =
      meta.get("type")
        .flatMap(t => MediachainTypes.fromCValue(t).toOption)
        .orElse(Some(MediachainTypes.EntityChainCell))

    override def toCbor = {
      val defaults = meta + ("entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  object EntityChainCell {
    def apply(entity: Reference, chain: Option[Reference], meta: Map[String, CValue]): EntityChainCell =
      new EntityChainCell(entity, chain, meta)

    def unapply(cell: EntityChainCell): Option[(Reference, Option[Reference], Map[String, CValue])] =
      Some((cell.entity, cell.chain, cell.meta))
  }

  class ArtefactChainCell(
    val artefact: Reference,
    val chain: Option[Reference],
    val meta: Map[String, CValue]
  ) extends ChainCell {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactChainCell(artefact, xchain, meta)
    
    val mediachainType: Option[MediachainType] =
      meta.get("type")
        .flatMap(t => MediachainTypes.fromCValue(t).toOption)
        .orElse(Some(MediachainTypes.ArtefactChainCell))

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  object ArtefactChainCell {
    def apply(artefact: Reference, chain: Option[Reference], meta: Map[String, CValue]): ArtefactChainCell =
      new ArtefactChainCell(artefact, chain, meta)

    def unapply(cell: ArtefactChainCell): Option[(Reference, Option[Reference], Map[String, CValue])] =
      Some((cell.artefact, cell.chain, cell.meta))
  }


  // Entity Chain cell subtypes
  case class EntityUpdateCell(
    override val entity: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String,CValue]
  ) extends EntityChainCell(entity, chain, meta) {
    override def cons(xchain: Option[Reference]): ChainCell =
      EntityUpdateCell(entity, xchain, meta)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.EntityUpdateCell)
  }

  case class EntityLinkCell(
    override val entity: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    entityLink: Reference
  ) extends EntityChainCell(entity, chain, meta)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      EntityLinkCell(entity, xchain, meta, entityLink)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.EntityLinkCell)

    override def toCbor = {
      val defaults = meta + ("entity" -> entity.toCbor) +
        ("entityLink" -> entityLink.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }


  // Artefact chain cell subtypes
  case class ArtefactUpdateCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue]
  ) extends ArtefactChainCell(artefact, chain, meta) {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactUpdateCell(artefact, xchain, meta)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactUpdateCell)
  }

  case class ArtefactCreationCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    entity: Reference
  ) extends ArtefactChainCell(artefact, chain, meta)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactCreationCell(artefact, xchain, meta, entity)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactCreationCell)

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor) +
        ("entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  case class ArtefactDerivationCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    artefactOrigin: Reference
  ) extends ArtefactChainCell(artefact, chain, meta)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactDerivationCell(artefact, xchain, meta, artefactOrigin)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactDerivationCell)

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor) +
        ("artefactOrigin" -> artefactOrigin.toCbor)

      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  case class ArtefactOwnershipCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    entity: Reference
  ) extends ArtefactChainCell(artefact, chain, meta)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactOwnershipCell(artefact, xchain, meta, entity)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactOwnershipCell)

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor) +
        ("entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  case class ArtefactReferenceCell(
    override val artefact: Reference,
    override val chain: Option[Reference],
    override val meta: Map[String, CValue],
    entity: Reference
  ) extends ArtefactChainCell(artefact, chain, meta)
  {
    override def cons(xchain: Option[Reference]): ChainCell =
      ArtefactReferenceCell(artefact, xchain, meta, entity)

    override val mediachainType: Option[MediachainType] =
      Some(MediachainTypes.ArtefactReferenceCell)

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor) +
        ("entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
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
    val mediachainType: Option[MediachainType] = Some(MediachainTypes.CanonicalEntry)

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
    val mediachainType: Option[MediachainType] = Some(MediachainTypes.ChainEntry)

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
    val mediachainType: Option[MediachainType] = Some(MediachainTypes.JournalBlock)

    override def toCbor = {
      val cborEntries = CArray(entries.map(_.toCbor).toList)
      val defaults = Map("index" -> CInt(index), "entries" -> cborEntries)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }
}
