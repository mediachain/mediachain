package io.mediachain.transactor

import java.nio.charset.StandardCharsets

import io.mediachain.BaseSpec
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.CborSerialization
import org.specs2.matcher.Matcher

object CborSerializationSpec extends BaseSpec {
  import io.mediachain.protocol.Datastore._
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
          - artefact update cell $roundTripArtefactUpdateCell
          - artefact creation cell $roundTripArtefactCreationCell
          - artefact derivation cell $roundTripArtefactDerivationCell
          - artefact ownership cell $roundTripArtefactOwnershipCell
          - artefact reference cell $roundTripArtefactReferenceCell

          - canonical journal entry $roundTripCanonicalEntry
          - chain journal entry $roundTripChainEntry
          - journal block $roundTripJournalBlock

       - decodes to base chain cell types when using transactorDeserializers $transactorDeserializersDecodesToBaseTypes
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

    val artefactUpdateCell = ArtefactUpdateCell(
      artefact = multihashRef("bar"),
      chain = Some(multihashRef("zork")),
      meta = Map("medium" -> CString("popsicle sticks"))
    )

    val artefactCreationCell = ArtefactCreationCell(
      artefact = multihashRef("bar"),
      entity = multihashRef("baz"),
      chain = Some(multihashRef("zork")),
      meta = Map("creationDate" -> CString("1984"))
    )

    val artefactDerivationCell = ArtefactDerivationCell(
      artefact = multihashRef("bar"),
      artefactOrigin = multihashRef("baz"),
      chain = Some(multihashRef("zork")),
      meta = Map("creationDate" -> CString("1985"))
    )

    val artefactOwnershipCell = ArtefactOwnershipCell(
      artefact = multihashRef("bar"),
      entity = multihashRef("baz"),
      chain = Some(multihashRef("zork")),
      meta = Map("creationDate" -> CString("1984"))
    )

    val artefactReferenceCell = ArtefactReferenceCell(
      artefact = multihashRef("bar"),
      entity = multihashRef("baz"),
      chain = Some(multihashRef("zork")),
      meta = Map("creationDate" -> CString("1984"))
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

  def matchEntityChainCell(expected: EntityChainCell): Matcher[EntityChainCell] =
    beLike {
      case c: EntityChainCell => {
        c.entity must_== expected.entity
        c.chain must_== expected.chain
        c.meta must havePairs(expected.meta.toList:_*)
      }
    }

  def matchArtefactChainCell(expected: ArtefactChainCell): Matcher[ArtefactChainCell] =
    beLike {
      case c: ArtefactChainCell => {
        c.artefact must_== expected.artefact
        c.chain must_== expected.chain
        c.meta must havePairs(expected.meta.toList:_*)
      }
    }

  def encodesTypeName = {
    Fixtures.entity.toCbor must matchTypeName(MediachainTypes.Entity)
    Fixtures.artefact.toCbor must matchTypeName(MediachainTypes.Artefact)
    Fixtures.entityChainCell.toCbor must matchTypeName(MediachainTypes.EntityChainCell)
    Fixtures.entityUpdateCell.toCbor must matchTypeName(MediachainTypes.EntityUpdateCell)
    Fixtures.entityLinkCell.toCbor must matchTypeName(MediachainTypes.EntityLinkCell)
    Fixtures.artefactChainCell.toCbor must matchTypeName(MediachainTypes.ArtefactChainCell)
    Fixtures.artefactUpdateCell.toCbor must matchTypeName(MediachainTypes.ArtefactUpdateCell)
    Fixtures.artefactCreationCell.toCbor must matchTypeName(MediachainTypes.ArtefactCreationCell)
    Fixtures.artefactDerivationCell.toCbor must matchTypeName(MediachainTypes.ArtefactDerivationCell)
    Fixtures.artefactOwnershipCell.toCbor must matchTypeName(MediachainTypes.ArtefactOwnershipCell)
    Fixtures.artefactReferenceCell.toCbor must matchTypeName(MediachainTypes.ArtefactReferenceCell)
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
      entityCell must matchEntityChainCell(Fixtures.entityChainCell)
    }

  def roundTripEntityUpdateCell =
    fromCbor(Fixtures.entityUpdateCell.toCbor) must beRightXor { c =>
      val cell = c.asInstanceOf[EntityUpdateCell]
      cell must matchEntityChainCell(Fixtures.entityUpdateCell)
    }

  def roundTripEntityLinkCell =
    fromCbor(Fixtures.entityLinkCell.toCbor) must beRightXor { c =>
      val cell = c.asInstanceOf[EntityLinkCell]
      cell must matchEntityChainCell(Fixtures.entityLinkCell)
      cell.entityLink must_== Fixtures.entityLinkCell.entityLink
    }

  def roundTripArtefactChainCell =
    fromCbor(Fixtures.artefactChainCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactChainCell]
      artefactCell must matchArtefactChainCell(Fixtures.artefactChainCell)
    }

  def roundTripArtefactUpdateCell =
    fromCbor(Fixtures.artefactUpdateCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactUpdateCell]
      artefactCell must matchArtefactChainCell(Fixtures.artefactUpdateCell)
    }

  def roundTripArtefactCreationCell =
    fromCbor(Fixtures.artefactCreationCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactCreationCell]
      artefactCell must matchArtefactChainCell(Fixtures.artefactCreationCell)
      artefactCell.entity must_== Fixtures.artefactCreationCell.entity
    }

  def roundTripArtefactDerivationCell =
    fromCbor(Fixtures.artefactDerivationCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactDerivationCell]
      artefactCell must matchArtefactChainCell(Fixtures.artefactDerivationCell)
      artefactCell.artefactOrigin must_== Fixtures.artefactDerivationCell.artefactOrigin
    }

  def roundTripArtefactOwnershipCell =
    fromCbor(Fixtures.artefactOwnershipCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactOwnershipCell]
      artefactCell must matchArtefactChainCell(Fixtures.artefactOwnershipCell)
      artefactCell.entity must_== Fixtures.artefactOwnershipCell.entity
    }

  def roundTripArtefactReferenceCell =
    fromCbor(Fixtures.artefactReferenceCell.toCbor) must beRightXor { cell =>
      val artefactCell = cell.asInstanceOf[ArtefactReferenceCell]
      artefactCell must matchArtefactChainCell(Fixtures.artefactReferenceCell)
      artefactCell.entity must_== Fixtures.artefactReferenceCell.entity
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

  def transactorDeserializersDecodesToBaseTypes = {
    implicit val deserializers = transactorDeserializers
    fromCbor(Fixtures.entityUpdateCell.toCbor) must beRightXor { cell =>
      cell must haveClass[EntityChainCell]
    }

    fromCbor(Fixtures.entityLinkCell.toCbor) must beRightXor { cell =>
      cell must haveClass[EntityChainCell]
    }

    fromCbor(Fixtures.artefactUpdateCell.toCbor) must beRightXor { cell =>
      cell must haveClass[ArtefactChainCell]
    }

    fromCbor(Fixtures.artefactDerivationCell.toCbor) must beRightXor { cell =>
      cell must haveClass[ArtefactChainCell]
    }

    fromCbor(Fixtures.artefactOwnershipCell.toCbor) must beRightXor { cell =>
      cell must haveClass[ArtefactChainCell]
    }

    fromCbor(Fixtures.artefactCreationCell.toCbor) must beRightXor { cell =>
      cell must haveClass[ArtefactChainCell]
    }

    fromCbor(Fixtures.artefactReferenceCell.toCbor) must beRightXor { cell =>
      cell must haveClass[ArtefactChainCell]
    }
  }
}
