package io.mediachain.types

import io.mediachain.multihash.MultiHash
import io.mediachain.types.CborSerialization.{CborSerializable, MediachainTypes}
import io.mediachain.types.TransactorTypes.{ArtefactChainReference, ChainReference, EntityChainReference}
import io.mediachain.util.cbor.CborAST._


object DatastoreTypes {

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
    val mediachainType = None

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
    val mediachainType = Some(MediachainTypes.Entity)

    override def toCbor =
      super.toCMapWithDefaults(meta, Map())

    def reference: ChainReference = EntityChainReference.empty
  }

  case class Artefact(
    meta: Map[String, CValue]
  ) extends CanonicalRecord {
    val mediachainType = Some(MediachainTypes.Artefact)

    override def toCbor =
      super.toCMapWithDefaults(meta, Map())

    def reference: ChainReference = ArtefactChainReference.empty
  }


  // Chain cells
  abstract class ChainCell extends Record {
    def chain: Option[Reference]
  }

  case class EntityChainCell(
    entity: Reference,
    chain: Option[Reference],
    meta: Map[String, CValue]
  ) extends ChainCell {
    val mediachainType = Some(MediachainTypes.EntityChainCell)

    override def toCbor = {
      val defaults = meta + ("entity" -> entity.toCbor)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }

  case class ArtefactChainCell(
    artefact: Reference,
    chain: Option[Reference],
    meta: Map[String, CValue]
  ) extends ChainCell {
    val mediachainType = Some(MediachainTypes.ArtefactChainCell)

    override def toCbor = {
      val defaults = meta + ("artefact" -> artefact.toCbor)
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
    val mediachainType = Some(MediachainTypes.CanonicalEntry)

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
    val mediachainType = Some(MediachainTypes.ChainEntry)

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
    val mediachainType = Some(MediachainTypes.JournalBlock)

    override def toCbor = {
      val cborEntries = CArray(entries.map(_.toCbor).toList)
      val defaults = Map("index" -> CInt(index), "entries" -> cborEntries)
      val optionals = Map("chain" -> chain.map(_.toCbor))
      super.toCMapWithDefaults(defaults, optionals)
    }
  }
}