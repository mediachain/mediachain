package io.mediachain.transactor

import io.mediachain.BaseSpec
import org.specs2.matcher.Matcher

object CborSerializationSpec extends BaseSpec {
  import io.mediachain.transactor.Types._
  import io.mediachain.transactor.CborSerialization._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.transactor.Dummies.DummyReference

  def is =
    s2"""
         - encodes the CBOR type name correctly $encodesTypeName

         round-trip converts to/from CBOR
          - entity $roundTripEntity
          - artefact $roundTripArtefact
          - entity chain cell $roundTripEntityChainCell
          - artefact chain cell $roundTripArtefactChainCell
          - canonical journal entry $roundTripCanonicalEntry
          - chain journal entry $roundTripChainEntry
          - journal block $roundTripJournalBlock
      """

  private object Fixtures {

    val entity = Entity(meta = Map("foo" -> CString("bar")))
    val artefact = Artefact(meta = Map("bar" -> CString("baz")))

    val entityChainCell = EntityChainCell(
      entity = new DummyReference(0),
      chain = None,
      meta = Map("created" -> CString("the past"))
    )

    val artefactChainCell = ArtefactChainCell(
      artefact = new DummyReference(1),
      chain = None,
       meta = Map("created" -> CString("the past"))
    )

    val canonicalEntry = CanonicalEntry(
      index = 42,
      ref = new DummyReference(0)
    )

    val chainEntry = ChainEntry(
      index = 43,
      ref = new DummyReference(0),
      chain = new DummyReference(2),
      chainPrevious = None
    )

    val journalBlock = JournalBlock(
      index = 44,
      chain = Some(new DummyReference(3)),
      entries = Array(canonicalEntry, chainEntry)
    )
  }

  def matchTypeName(typeName: String): Matcher[CValue] =
    beLike {
      case m: CMap =>
        m.asStringKeyedMap must havePair ("type" -> CString(typeName))
    }

  def encodesTypeName = {
    Fixtures.entity.toCbor must matchTypeName(CBORTypeNames.Entity)
    Fixtures.artefact.toCbor must matchTypeName(CBORTypeNames.Artefact)
    Fixtures.entityChainCell.toCbor must matchTypeName(CBORTypeNames.EntityChainCell)
    Fixtures.artefactChainCell.toCbor must matchTypeName(CBORTypeNames.ArtefactChainCell)
    Fixtures.canonicalEntry.toCbor must matchTypeName(CBORTypeNames.CanonicalEntry)
    Fixtures.chainEntry.toCbor must matchTypeName(CBORTypeNames.ChainEntry)
    Fixtures.journalBlock.toCbor must matchTypeName(CBORTypeNames.JournalBlock)
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
