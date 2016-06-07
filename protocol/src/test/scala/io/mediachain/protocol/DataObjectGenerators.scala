package io.mediachain.protocol

import java.nio.charset.StandardCharsets
import java.util.Date

object DataObjectGenerators {
  import org.scalacheck._
  import Arbitrary.arbitrary

  import io.mediachain.multihash.MultiHash
  import io.mediachain.protocol.Datastore._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.util.cbor.CValueGenerators._
  import io.mediachain.util.gen.GenInstances._

  import cats.syntax.cartesian._
  import cats.std.option.optionInstance
  import cats.Applicative

  // arbitrary string metadata kv pairs
  val genStringMetas: List[Gen[(String, CValue)]] = (1 to 10).toList.map { _ =>
    (Gen.alphaStr |@| genCPrimitive).map((_, _))
  }

  // a date field
  val genDateMeta: Gen[(String, CString)] =
    arbitrary[Date].map(date => ("date", CString(date.toString)))

  // some named fields for fold testing
  val genNamedMetas: List[Gen[(String, CValue)]] = List("AAA", "BBB", "CCC").map { k =>
    Gen.alphaStr.map(x => (k, CString(x)))
  }

  val genMeta: Gen[Map[String, CValue]] = for {
    meta <- Gen.someOf[(String, CValue)](genDateMeta, genDateMeta, genStringMetas ++ genNamedMetas:_*)
  } yield meta.toMap

  val genReference: Gen[Reference] = arbitrary[String].map { x =>
    val hash = MultiHash.hashWithSHA256(x.getBytes(StandardCharsets.UTF_8))
    MultihashReference(hash)
  }

  val genOptionalReference: Gen[Option[Reference]] =
    Gen.option(genReference)

  val genNilReference: Gen[Option[Reference]] = Gen.const(None)

  val genMetaSource = genOptionalReference

  val genEntity: Gen[Entity] = genMeta.map(meta => Entity(meta))
  val genArtefact: Gen[Artefact] = genMeta.map(meta => Artefact(meta))

  def genReferenceFor(canonicalGen: Gen[CanonicalRecord]): Gen[Reference] =
    canonicalGen.map(MultihashReference.forDataObject)

  def genEntityChainCell(
    entityGen: Gen[Entity],
    chainGen: Gen[Option[Reference]]
  ): Gen[EntityChainCell] =
    (genReferenceFor(entityGen) |@| chainGen |@| genMeta |@| genMetaSource)
      .map(EntityChainCell.apply)

  val genEntityChainCell: Gen[EntityChainCell] = genEntityChainCell(genEntity, genOptionalReference)

  def genArtefactChainCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]]
  ): Gen[ArtefactChainCell] =
    (genReferenceFor(artefactGen) |@| chainGen |@| genMeta |@| genMetaSource)
      .map(ArtefactChainCell.apply)

  val genArtefactChainCell: Gen[ArtefactChainCell] = genArtefactChainCell(genArtefact, genOptionalReference)

  def genEntityUpdateCell(
    entityGen: Gen[Entity],
    chainGen: Gen[Option[Reference]]
  ) = genEntityChainCell(entityGen, chainGen).map { base =>
    EntityUpdateCell(base.entity, base.chain, base.meta, base.metaSource)
  }

  val genEntityUpdateCell: Gen[EntityUpdateCell] =
    genEntityUpdateCell(genEntity, genOptionalReference)

  def genEntityLinkCell(
    entityGen: Gen[Entity],
    chainGen: Gen[Option[Reference]],
    entityLinkGen: Gen[Reference]
  ) = for {
    base <- genEntityChainCell(entityGen, chainGen)
    entityLink <- entityLinkGen
  } yield EntityLinkCell(base.entity, base.chain, base.meta, base.metaSource, entityLink)
  val genEntityLinkCell: Gen[EntityLinkCell] =
    genEntityLinkCell(genEntity, genOptionalReference, genReference)

  def genArtefactUpdateCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]]
  ) = genArtefactChainCell(artefactGen, chainGen).map { base =>
    ArtefactUpdateCell(base.artefact, base.chain, base.meta, base.metaSource)
  }
  val genArtefactUpdateCell: Gen[ArtefactUpdateCell] =
    genArtefactUpdateCell(genArtefact, genOptionalReference)

  def genArtefactLinkCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]],
    artefactLinkGen: Gen[Reference]
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    artefactLink <- artefactLinkGen
  } yield ArtefactLinkCell(base.artefact, base.chain, base.meta, base.metaSource, artefactLink)
  val genArtefactLinkCell: Gen[ArtefactLinkCell] =
    genArtefactLinkCell(genArtefact, genOptionalReference, genReference)

  def genArtefactCreationCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]],
    entityGen: Gen[Reference]
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    entity <- entityGen
  } yield ArtefactCreationCell(base.artefact, base.chain, base.meta, base.metaSource, entity)
  val genArtefactCreationCell: Gen[ArtefactCreationCell] =
    genArtefactCreationCell(genArtefact, genOptionalReference, genReference)

  def genArtefactDerivationCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]],
    artefactOriginGen: Gen[Reference]
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    artefactOrigin <- artefactOriginGen
  } yield ArtefactDerivationCell(base.artefact, base.chain, base.meta, base.metaSource, artefactOrigin)
  val genArtefactDerivationCell: Gen[ArtefactDerivationCell] =
    genArtefactDerivationCell(genArtefact, genOptionalReference, genReference)

  def genArtefactOwnershipCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]],
    entityGen: Gen[Reference]
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    entity <- entityGen
  } yield ArtefactOwnershipCell(base.artefact, base.chain, base.meta, base.metaSource, entity)
  val genArtefactOwnershipCell: Gen[ArtefactOwnershipCell] =
    genArtefactOwnershipCell(genArtefact, genOptionalReference, genReference)

  def genArtefactReferenceCell(
    artefactGen: Gen[Artefact],
    chainGen: Gen[Option[Reference]],
    entityGen: Gen[Option[Reference]]
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    entity <- entityGen
  } yield ArtefactReferenceCell(base.artefact, base.chain, base.meta, base.metaSource, entity)
  val genArtefactReferenceCell: Gen[ArtefactReferenceCell] =
    genArtefactReferenceCell(genArtefact, genOptionalReference, genOptionalReference)

  val genCanonicalEntry =
    (arbitrary[BigInt] |@| genReference).map(CanonicalEntry)

  val genChainEntry =
    (arbitrary[BigInt] |@|
      genReference |@|
      genReference |@|
      genReference.map(Applicative[Option].pure)
      ).map(ChainEntry)

  val genJournalBlock = for {
    index <- arbitrary[BigInt]
    chain <- genReference.map(Applicative[Option].pure)
    entries <- Gen.containerOf[Array, JournalEntry](
      Gen.oneOf(genCanonicalEntry, genChainEntry)
    )
  } yield JournalBlock(index, chain, entries)

  implicit def abEntity: Arbitrary[Entity] = Arbitrary(genEntity)
  implicit def abArtefact: Arbitrary[Artefact] = Arbitrary(genArtefact)
  implicit def abEntityChainCell: Arbitrary[EntityChainCell] = Arbitrary(genEntityChainCell)
  implicit def abArtefactChainCell: Arbitrary[ArtefactChainCell] = Arbitrary(genArtefactChainCell)
  implicit def abEntityUpdateCell: Arbitrary[EntityUpdateCell] = Arbitrary(genEntityUpdateCell)
  implicit def abEntityLinkCell: Arbitrary[EntityLinkCell] = Arbitrary(genEntityLinkCell)
  implicit def abArtefactUpdateCell: Arbitrary[ArtefactUpdateCell] = Arbitrary(genArtefactUpdateCell)
  implicit def abArtefactLinkCell: Arbitrary[ArtefactLinkCell] = Arbitrary(genArtefactLinkCell)
  implicit def abArtefactCreationCell: Arbitrary[ArtefactCreationCell] = Arbitrary(genArtefactCreationCell)
  implicit def abArtefactDerivationCell: Arbitrary[ArtefactDerivationCell] = Arbitrary(genArtefactDerivationCell)
  implicit def abArtefactOwnershipCell: Arbitrary[ArtefactOwnershipCell] = Arbitrary(genArtefactOwnershipCell)
  implicit def abArtefactReferenceCell: Arbitrary[ArtefactReferenceCell] = Arbitrary(genArtefactReferenceCell)
  implicit def abCanonicalEntry: Arbitrary[CanonicalEntry] = Arbitrary(genCanonicalEntry)
  implicit def abChainEntry: Arbitrary[ChainEntry] = Arbitrary(genChainEntry)
  implicit def abJournalBlock: Arbitrary[JournalBlock] = Arbitrary(genJournalBlock)
}
