package io.mediachain.transactor

import java.nio.charset.StandardCharsets

import io.mediachain.BaseSpec
import io.mediachain.multihash.MultiHash
import io.mediachain.types.CborSerialization
import org.specs2.matcher.Matcher

object CborSerializationSpec extends BaseSpec {
  import io.mediachain.types.Datastore._
  import CborSerialization._
  import io.mediachain.util.cbor.CborAST._

  def is =
    s2"""
         - encodes the CBOR type name correctly $encodesTypeName

         round-trip converts to/from CBOR
          - entity $roundTripEntity
          - artefact $roundTripArtefact
          - entity chain cell $roundTripEntityChainCell
          - entity update cell $roundTripEntityUpdateCell
          - entity link cell $roundTripEntityLinkCell
          - artefact chain cell $roundTripArtefactChainCell
          - canonical journal entry $roundTripCanonicalEntry
          - chain journal entry $roundTripChainEntry
          - journal block $roundTripJournalBlock
      """

  def multihashRef(content: String): MultihashReference = {
    MultihashReference(
      MultiHash.hashWithSHA256(content.getBytes(StandardCharsets.UTF_8))
    )
  }

  private object Fixtures {

    val entity = Entity(meta = Map("foo" -> CString("bar")))
    val artefact = Artefact(meta = Map("bar" -> CString("baz")))

    val entityChainCell = EntityChainCell(
      entity = multihashRef("foo"),
      chain = None,
      meta = Map("created" -> CString("the past"))
    )

    val entityUpdateCell = EntityUpdateCell(
      entity = multihashRef("foo"),
      chain = Some(multihashRef("bar")),
      meta = Map("favoriteColor" -> CString("Blue! No, Yellow!!!"))
    )

    val entityLinkCell = EntityLinkCell(
      entity = multihashRef("foo"),
      chain = Some(multihashRef("baz")),
      meta = Map("relationshipType" -> CString("Soul brothers")),
      entityLink = multihashRef("biff")
    )

    val artefactChainCell = ArtefactChainCell(
      artefact = multihashRef("bar"),
      chain = None,
       meta = Map("created" -> CString("the past"))
    )

    val canonicalEntry = CanonicalEntry(
      index = 42,
      ref = multihashRef("foo")
    )

    val chainEntry = ChainEntry(
      index = 43,
      ref = multihashRef("foo"),
      chain = multihashRef("baz"),
      chainPrevious = None
    )

    val journalBlock = JournalBlock(
      index = 44,
      chain = Some(multihashRef("blammo")),
      entries = Array(canonicalEntry, chainEntry)
    )
  }

  def matchTypeName(typeName: MediachainType): Matcher[CValue] =
    beLike {
      case m: CMap =>
        m.asStringKeyedMap must havePair ("type" -> CString(typeName.stringValue))
    }

  def encodesTypeName = {
    Fixtures.entity.toCbor must matchTypeName(MediachainTypes.Entity)
    Fixtures.artefact.toCbor must matchTypeName(MediachainTypes.Artefact)
    Fixtures.entityChainCell.toCbor must matchTypeName(MediachainTypes.EntityChainCell)
    Fixtures.entityUpdateCell.toCbor must matchTypeName(MediachainTypes.EntityUpdateCell)
    Fixtures.entityLinkCell.toCbor must matchTypeName(MediachainTypes.EntityLinkCell)
    Fixtures.artefactChainCell.toCbor must matchTypeName(MediachainTypes.ArtefactChainCell)
    Fixtures.canonicalEntry.toCbor must matchTypeName(MediachainTypes.CanonicalEntry)
    Fixtures.chainEntry.toCbor must matchTypeName(MediachainTypes.ChainEntry)
    Fixtures.journalBlock.toCbor must matchTypeName(MediachainTypes.JournalBlock)
  }

  def roundTripEntity =
    fromCbor(Fixtures.entity.toCbor) must beRightXor { entity =>
      entity.asInstanceOf[Entity].meta must havePairs(Fixtures.entity.meta.toList:_*)
    }

  def roundTripArtefact =
    fromCbor(Fixtures.artefact.toCbor) must beRightXor { entity =>
      entity.asInstanceOf[Artefact].meta must havePairs(Fixtures.artefact.meta.toList:_*)
    }

  def roundTripEntityChainCell =
    fromCbor(Fixtures.entityChainCell.toCbor) must beRightXor { cell =>
      val entityCell = cell.asInstanceOf[EntityChainCell]
      entityCell.entity must_== Fixtures.entityChainCell.entity
      entityCell.chain must_== Fixtures.entityChainCell.chain
      entityCell.meta must havePairs(Fixtures.entityChainCell.meta.toList:_*)
    }

  def roundTripEntityUpdateCell =
    fromCbor(Fixtures.entityUpdateCell.toCbor) must beRightXor { c =>
      val cell = c.asInstanceOf[EntityUpdateCell]
      cell.entity must_== Fixtures.entityUpdateCell.entity
      cell.chain must_== Fixtures.entityUpdateCell.chain
      cell.meta must havePairs(Fixtures.entityUpdateCell.meta.toList:_*)
    }

  def roundTripEntityLinkCell =
    fromCbor(Fixtures.entityLinkCell.toCbor) must beRightXor { c =>
      val cell = c.asInstanceOf[EntityLinkCell]
      cell.entity must_== Fixtures.entityLinkCell.entity
      cell.chain must_== Fixtures.entityLinkCell.chain
      cell.entityLink must_== Fixtures.entityLinkCell.entityLink
      cell.meta must havePairs(Fixtures.entityLinkCell.meta.toList:_*)
    }

  def roundTripArtefactChainCell =
    fromCbor(Fixtures.artefactChainCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactChainCell]
      artefactCell.artefact must_== Fixtures.artefactChainCell.artefact
      artefactCell.chain must_== Fixtures.artefactChainCell.chain
      artefactCell.meta must havePairs(Fixtures.artefactChainCell.meta.toList:_*)
    }

  def roundTripCanonicalEntry =
    fromCbor(Fixtures.canonicalEntry.toCbor) must beRightXor { cell =>
      cell.asInstanceOf[CanonicalEntry] must_== Fixtures.canonicalEntry
    }

  def roundTripChainEntry =
    fromCbor(Fixtures.chainEntry.toCbor) must beRightXor { cell =>
      cell.asInstanceOf[ChainEntry] must_== Fixtures.chainEntry
    }

  def roundTripJournalBlock =
    fromCbor(Fixtures.journalBlock.toCbor) must beRightXor { cell =>
      val expected = Fixtures.journalBlock

      cell must beLike {
        case block: JournalBlock => {
          block.index must_== expected.index

          block.chain must_== expected.chain

          block.entries.toList must containTheSameElementsAs(expected.entries.toList)
        }
      }
    }
}
