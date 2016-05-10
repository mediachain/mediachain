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

  val stringMetaGens: List[Gen[(String, CValue)]] = (1 to 25).toList.map { _ =>
    for {
      key <- Gen.alphaStr
      value <- genCPrimitive
    } yield (key, value)
  }

  val dateMetaGen: Gen[(String, CString)] = for {
    date <- arbitrary[Date]
  } yield ("date", CString(date.toString))

  val authorMetaGen: Gen[(String, CString)] = for {
    author <- arbitrary[String] // TODO: make this a MultiHash etc
  } yield ("author", CString(author))

  val genMeta: Gen[Map[String, CValue]] = for {
    meta <- Gen.someOf[(String, CValue)](authorMetaGen, dateMetaGen, stringMetaGens:_*)
  } yield meta.toMap

  val genReference: Gen[Reference] = for {
    str <- arbitrary[String]
  } yield MultihashReference(
    MultiHash.hashWithSHA256(str.getBytes(StandardCharsets.UTF_8))
  )

  val genOptionalReference: Gen[Option[Reference]] =
    Gen.option(genReference)

  val genNilReference: Gen[Option[Reference]] = Gen.const(None)

  val genEntity = for {
    meta <- genMeta
  } yield Entity(meta)

  val genArtefact = for {
    meta <- genMeta
  } yield Artefact(meta)

  def genReferenceFor(canonicalGen: Gen[CanonicalRecord]): Gen[Reference] =
    for {
      canonical <- canonicalGen
    } yield MultihashReference.forDataObject(canonical)

  def genEntityChainCell(
    entityGen: Gen[Entity] = genEntity,
    chainGen: Gen[Option[Reference]] = genOptionalReference
  ) = for {
    entity <- genReferenceFor(entityGen)
    chain <- chainGen
    meta <- genMeta
  } yield EntityChainCell(entity, chain, meta)

  def genArtefactChainCell(
    artefactGen: Gen[Artefact] = genArtefact,
    chainGen: Gen[Option[Reference]] = genOptionalReference
  ) = for {
    artefact <- genReferenceFor(artefactGen)
    chain <- chainGen
    meta <- genMeta
  } yield ArtefactChainCell(artefact, chain, meta)

  def genEntityUpdateCell(
    entityGen: Gen[Entity] = genEntity,
    chainGen: Gen[Option[Reference]] = genOptionalReference
  ) = for {
    base <- genEntityChainCell(entityGen, chainGen)
  } yield EntityUpdateCell(base.entity, base.chain, base.meta)

  def genEntityLinkCell(
    entityGen: Gen[Entity] = genEntity,
    chainGen: Gen[Option[Reference]] = genOptionalReference,
    entityLinkGen: Gen[Reference] = genReference
  ) = for {
    base <- genEntityChainCell(entityGen, chainGen)
    entityLink <- entityLinkGen
  } yield EntityLinkCell(base.entity, base.chain, base.meta, entityLink)

  def genArtefactUpdateCell(
    artefactGen: Gen[Artefact] = genArtefact,
    chainGen: Gen[Option[Reference]] = genOptionalReference
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
  } yield ArtefactUpdateCell(base.artefact, base.chain, base.meta)

  def genArtefactCreationCell(
    artefactGen: Gen[Artefact] = genArtefact,
    chainGen: Gen[Option[Reference]] = genOptionalReference,
    entityGen: Gen[Reference] = genReference
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    entity <- entityGen
  } yield ArtefactCreationCell(base.artefact, base.chain, base.meta, entity)

  def genArtefactDerivationCell(
    artefactGen: Gen[Artefact] = genArtefact,
    chainGen: Gen[Option[Reference]] = genOptionalReference,
    artefactOriginGen: Gen[Reference] = genReference
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    artefactOrigin <- artefactOriginGen
  } yield ArtefactDerivationCell(base.artefact, base.chain, base.meta, artefactOrigin)

  def genArtefactOwnershipCell(
    artefactGen: Gen[Artefact] = genArtefact,
    chainGen: Gen[Option[Reference]] = genOptionalReference,
    entityGen: Gen[Reference] = genReference
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    entity <- entityGen
  } yield ArtefactOwnershipCell(base.artefact, base.chain, base.meta, entity)

  def genArtefactReferenceCell(
    artefactGen: Gen[Artefact] = genArtefact,
    chainGen: Gen[Option[Reference]] = genOptionalReference,
    entityGen: Gen[Reference] = genReference
  ) = for {
    base <- genArtefactChainCell(artefactGen, chainGen)
    entity <- entityGen
  } yield ArtefactReferenceCell(base.artefact, base.chain, base.meta, entity)

  val genCanonicalEntry = for {
    index <- arbitrary[BigInt]
    ref <- genReference
  } yield CanonicalEntry(index, ref)

  val genChainEntry = for {
    index <- arbitrary[BigInt]
    ref <- genReference
    chain <- genReference
    chainPrevious <- genReference
  } yield ChainEntry(index, ref, chain, Some(chainPrevious))

  val genJournalBlock = for {
    index <- arbitrary[BigInt]
    chain <- genReference
    entries <- Gen.containerOf[Array, JournalEntry](Gen.oneOf(genCanonicalEntry, genChainEntry))
  } yield JournalBlock(index, Some(chain), entries)


  implicit def abEntity: Arbitrary[Entity] = Arbitrary(genEntity)
  implicit def abArtefact: Arbitrary[Artefact] = Arbitrary(genArtefact)
  implicit def abEntityChainCell: Arbitrary[EntityChainCell] = Arbitrary(genEntityChainCell())
  implicit def abArtefactChainCell: Arbitrary[ArtefactChainCell] = Arbitrary(genArtefactChainCell())
  implicit def abEntityUpdateCell: Arbitrary[EntityUpdateCell] = Arbitrary(genEntityUpdateCell())
  implicit def abEntityLinkCell: Arbitrary[EntityLinkCell] = Arbitrary(genEntityLinkCell())
  implicit def abArtefactUpdateCell: Arbitrary[ArtefactUpdateCell] = Arbitrary(genArtefactUpdateCell())
  implicit def abArtefactCreationCell: Arbitrary[ArtefactCreationCell] = Arbitrary(genArtefactCreationCell())
  implicit def abArtefactDerivationCell: Arbitrary[ArtefactDerivationCell] = Arbitrary(genArtefactDerivationCell())
  implicit def abArtefactOwnershipCell: Arbitrary[ArtefactOwnershipCell] = Arbitrary(genArtefactOwnershipCell())
  implicit def abArtefactReferenceCell: Arbitrary[ArtefactReferenceCell] = Arbitrary(genArtefactReferenceCell())
  implicit def abCanonicalEntry: Arbitrary[CanonicalEntry] = Arbitrary(genCanonicalEntry)
  implicit def abChainEntry: Arbitrary[ChainEntry] = Arbitrary(genChainEntry)
  implicit def abJournalBlock: Arbitrary[JournalBlock] = Arbitrary(genJournalBlock)
}
