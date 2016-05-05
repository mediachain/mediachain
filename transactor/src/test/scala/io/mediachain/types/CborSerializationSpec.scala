package io.mediachain.types

import io.mediachain.BaseSpec
import org.specs2.ScalaCheck


object CborSerializationSpec extends BaseSpec with ScalaCheck {
  import CborSerialization._
  import io.mediachain.types.DataObjectGenerators._
  import io.mediachain.types.Datastore._
  import io.mediachain.util.cbor.CborAST._
  import org.scalacheck.{Arbitrary, Gen}
  import org.scalacheck.Test.Parameters
  import org.specs2.matcher.Matcher

  def is =
    s2"""
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


  implicit val scalaCheckParams = Parameters.default
    .withMinSuccessfulTests(10) // # of tests needed to pass before marking as success
    .withMaxSize(5) // # of items to generate for containers (lists, etc)


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


  def roundTripEntity = prop { e: Entity =>
    val cbor = e.toCbor
    cbor must matchTypeName(MediachainTypes.Entity)

    fromCbor(cbor) must beRightXor { obj =>
      obj.asInstanceOf[Entity].meta must havePairs(e.meta.toList: _*)
    }
  }


  def roundTripArtefact = prop { a: Artefact =>
    val cbor = a.toCbor
    cbor must matchTypeName(MediachainTypes.Artefact)

    fromCbor(cbor) must beRightXor { obj =>
      obj.asInstanceOf[Artefact].meta must havePairs(a.meta.toList: _*)
    }
  }

  def roundTripEntityChainCell = prop { c: EntityChainCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.EntityChainCell)

    fromCbor(cbor) must beRightXor { obj =>
      obj.asInstanceOf[EntityChainCell] must matchEntityChainCell(c)
    }
  }.setArbitrary(Arbitrary(genEntityChainCell))

  def roundTripEntityUpdateCell = prop { c: EntityUpdateCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.EntityUpdateCell)

    fromCbor(cbor) must beRightXor { obj =>
      obj.asInstanceOf[EntityUpdateCell] must matchEntityChainCell(c)
    }
  }

  def roundTripEntityLinkCell = prop { c: EntityLinkCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.EntityLinkCell)

    fromCbor(cbor) must beRightXor { obj =>
      val cell = obj.asInstanceOf[EntityLinkCell]
      cell must matchEntityChainCell(c)
      cell.entityLink must_== c.entityLink
    }
  }

  def roundTripArtefactChainCell = prop { c: ArtefactChainCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.ArtefactChainCell)

    fromCbor(cbor) must beRightXor { obj =>
      val artefactCell = obj.asInstanceOf[ArtefactChainCell]
      artefactCell must matchArtefactChainCell(c)
    }
  }.setArbitrary(abArtefactChainCell)

  def roundTripArtefactUpdateCell = prop { c: ArtefactUpdateCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.ArtefactUpdateCell)

    fromCbor(cbor) must beRightXor { obj =>
      val artefactCell = obj.asInstanceOf[ArtefactUpdateCell]
      artefactCell must matchArtefactChainCell(c)
    }
  }

  def roundTripArtefactCreationCell = prop { c: ArtefactCreationCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.ArtefactCreationCell)

    fromCbor(cbor) must beRightXor { obj =>
      val artefactCell = obj.asInstanceOf[ArtefactCreationCell]
      artefactCell must matchArtefactChainCell(c)
      artefactCell.entity must_== c.entity
    }
  }

  def roundTripArtefactDerivationCell = prop { c: ArtefactDerivationCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.ArtefactDerivationCell)

    fromCbor(cbor) must beRightXor { obj =>
      val artefactCell = obj.asInstanceOf[ArtefactDerivationCell]
      artefactCell must matchArtefactChainCell(c)
      artefactCell.artefactOrigin must_== c.artefactOrigin
    }
  }

  def roundTripArtefactOwnershipCell = prop { c: ArtefactOwnershipCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.ArtefactOwnershipCell)

    fromCbor(cbor) must beRightXor { obj =>
      val artefactCell = obj.asInstanceOf[ArtefactOwnershipCell]
      artefactCell must matchArtefactChainCell(c)
      artefactCell.entity must_== c.entity
    }
  }

  def roundTripArtefactReferenceCell = prop { c: ArtefactReferenceCell =>
    val cbor = c.toCbor
    cbor must matchTypeName(MediachainTypes.ArtefactReferenceCell)

    fromCbor(cbor) must beRightXor { obj =>
      val artefactCell = obj.asInstanceOf[ArtefactReferenceCell]
      artefactCell must matchArtefactChainCell(c)
      artefactCell.entity must_== c.entity
    }
  }

  def roundTripCanonicalEntry = prop { e: CanonicalEntry =>
    val cbor = e.toCbor
    cbor must matchTypeName(MediachainTypes.CanonicalEntry)

    fromCbor(cbor) must beRightXor { obj =>
      obj.asInstanceOf[CanonicalEntry] must_== e
    }
  }

  def roundTripChainEntry = prop { e: ChainEntry =>
    val cbor = e.toCbor
    cbor must matchTypeName(MediachainTypes.ChainEntry)

    fromCbor(cbor) must beRightXor { obj =>
      obj.asInstanceOf[ChainEntry] must_== e
    }
  }

  def roundTripJournalBlock = prop { b: JournalBlock =>
    val cbor = b.toCbor
    cbor must matchTypeName(MediachainTypes.JournalBlock)

    fromCbor(cbor) must beRightXor { obj =>
      val expected = b

      obj must beLike {
        case block: JournalBlock => {
          block.index must_== expected.index

          block.chain must_== expected.chain

          block.entries.toList must containTheSameElementsAs(expected.entries.toList)
        }
      }
    }
  }

  def transactorDeserializersDecodesToBaseTypes =
    prop { c: ArtefactChainCell =>
      implicit val deserializers = transactorDeserializers
      fromCbor(c.toCbor) must beRightXor { obj =>
        obj must haveClass[ArtefactChainCell]
      }
    }.setGen(Gen.oneOf(
        genArtefactReferenceCell,
        genArtefactUpdateCell,
        genArtefactOwnershipCell,
        genArtefactDerivationCell,
        genArtefactCreationCell
      ))
}
