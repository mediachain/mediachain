package io.mediachain.protocol

import scala.util.Try

object JournalBlockGenerators {
  import io.mediachain.protocol.Datastore._
  import org.scalacheck._
  import DataObjectGenerators._


  /**
    * Make a generator that returns a chain cell for the given `CanonicalRecord`
    *
    * @param canonical either an `Entity` or `Artefact` to generate a cell for
    * @param entityReferences a list of references to Entities to choose from
    *                         when constructing relationships.  If empty,
    *                         only generic `EntityUpdateCell`s or
    *                         `ArtefactUpdateCell`s will be generated.
    * @return depending on the type of `CanonicalRecord` given, a generator for
    *         either a subtype of `EntityChainCell` or `EntityArtefactCell`.
    *         Note that the `chain` field of the generated cell will be
    *         set to `None`
    *
    */
  def genCellFor(canonical: CanonicalRecord,
    entityReferences: List[Reference],
    artefactReferences: List[Reference])
  : Gen[ChainCell] =
    canonical match {
      case e: Entity => genEntityCellFor(e, entityReferences)
      case a: Artefact => genArtefactCellFor(a, entityReferences, artefactReferences)
    }


  /**
    * Make a generator that returns an `EntityChainCell` for the given `Entity`
    *
    * @param entity the `Entity` to generate a cell for
    * @param entityReferences a list of references to Entities to choose from
    *                         when constructing relationships.  If empty,
    *                         only generic `EntityUpdateCell`s will be generated.
    * @return a generator for a subtype of `EntityChainCell`. Note that the
    *         `chain` field of the generated cell will be set to `None`
    */
  def genEntityCellFor(entity: Entity, entityReferences: List[Reference])
  : Gen[EntityChainCell] = {
    val entityGen = Gen.const(entity)
    if (entityReferences.isEmpty) {
      genEntityUpdateCell(entityGen, genNilReference)
    } else {
      Gen.oneOf(
        genEntityUpdateCell(entityGen, genNilReference),
        genEntityLinkCell(entityGen, genNilReference, Gen.oneOf(entityReferences))
      )
    }
  }

  /**
    * Make a generator that returns an `ArtefactChainCell` for the given `Artefact`
    *
    * @param artefact the `Artefact` to generate a cell for
    * @param entityReferences a list of references to Entities to choose from
    *                         when constructing relationships.  If empty,
    *                         only generic `ArtefactUpdateCell`s will be generated.
    * @param artefactReferences a list of references to Artefacts to choose from
    *                         when constructing relationships.  If empty,
    *                         only generic `ArtefactUpdateCell`s will be generated.
    * @return a generator for a subtype of `ArtefactChainCell`. Note that the
    *         `chain` field of the generated cell will be set to `None`
    */
  def genArtefactCellFor(
    artefact: Artefact,
    entityReferences: List[Reference],
    artefactReferences: List[Reference]
  ): Gen[ArtefactChainCell] = {
    val artefactGen = Gen.const(artefact)
    if (entityReferences.isEmpty || artefactReferences.isEmpty) {
      genArtefactUpdateCell(artefactGen, genNilReference)
    } else {
      Gen.oneOf(
        genArtefactUpdateCell(artefactGen, genNilReference),
        genArtefactCreationCell(artefactGen, genNilReference, Gen.oneOf(entityReferences)),
        genArtefactDerivationCell(artefactGen, genNilReference, Gen.oneOf(artefactReferences)),
        genArtefactOwnershipCell(artefactGen, genNilReference, Gen.oneOf(entityReferences)),
        genArtefactReferenceCell(artefactGen, genNilReference, Gen.oneOf(entityReferences))
      )
    }
  }


  def refForCell(cell: ChainCell): Reference = cell match {
    case e: EntityChainCell => e.entity
    case a: ArtefactChainCell => a.artefact
  }

  def consChainCells(cells: List[ChainCell]): List[ChainCell] = {
    import collection.mutable.{Map => MMap, MutableList => MList}
    val chainHeads: MMap[Reference, ChainCell] = MMap()
    val consed: MList[ChainCell] = MList()

    cells.foreach { c =>
      val canonicalRef = refForCell(c)

      val head = chainHeads.get(canonicalRef)
        .map(h => MultihashReference.forDataObject(h))

      val consedCell = c.cons(head)
      chainHeads.put(canonicalRef, consedCell)
      consed += consedCell
    }

    consed.toList
  }



  def entityReferences(canonicals: List[CanonicalRecord]): List[Reference] =
    canonicals.collect { case e: Entity => MultihashReference.forDataObject(e) }

  def artefactReferences(canonicals: List[CanonicalRecord]): List[Reference] =
    canonicals.collect { case a: Artefact => MultihashReference.forDataObject(a) }

  def genJournalBlock(
    blockSize: Int,
    datastore: Map[Reference, DataObject],
    blockchain: Option[JournalBlock]
  ): Gen[(JournalBlock, Map[Reference, DataObject])] = {
    // generate ~ twice as many chain cells as canonical entries
    val numCanonicals = (blockSize * 0.3).toInt
    val numChainCells = blockSize - numCanonicals

    // generate ~ 3x as many artefacts as entities
    val numEntities = (numCanonicals * 0.25).toInt
    val numArtefacts = numCanonicals - numEntities


    for {
      entities <- Gen.listOfN(numEntities, genEntity)
      artefacts <- Gen.listOfN(numArtefacts, genArtefact)

      canonicals = entities ++ artefacts
      entityRefs = entities.map(MultihashReference.forDataObject)
      artefactRefs = artefacts.map(MultihashReference.forDataObject)

      cellGen = Gen.oneOf(canonicals)
        .flatMap(genCellFor(_, entityRefs, artefactRefs))

      chainCells <- Gen.listOfN(numChainCells, cellGen).map(consChainCells)
    } yield {
      val startIndex: BigInt = blockchain.map(_.index).getOrElse(0)
      val entries = toJournalEntries(startIndex, canonicals, chainCells)
      val blockIndex = entries.lastOption.map(_.index + 1).getOrElse(startIndex)

      val prevBlockRef = blockchain.map(MultihashReference.forDataObject)
      val block = JournalBlock(blockIndex, prevBlockRef, entries.toArray)

      val generatedObjects = canonicals ++ chainCells
      val updatedDatastore = datastore ++ generatedObjects.map { o =>
        MultihashReference.forDataObject(o) -> o
      }

      (block, updatedDatastore)
    }
  }




  private def toJournalEntries(
    startIndex: BigInt,
    canonicals: List[CanonicalRecord],
    chainCells: List[ChainCell]): List[JournalEntry] = {
    // TODO: I'd like to interleave the canonical entries with the chain cell entries
    // but that's a bit more involved, since you need to make sure that the canonical
    // entry comes before any chain entries for that canonical

    val canonicalEntries: List[CanonicalEntry] =
      canonicals.zipWithIndex.map { pair =>
        val (c, i) = pair
        val index = startIndex + i
        CanonicalEntry(i, MultihashReference.forDataObject(c))
    }

    val chainStartIndex = startIndex + canonicalEntries.length

    val chainEntries: List[ChainEntry] =
      chainCells.zipWithIndex.map { pair =>
        val (c: ChainCell, i: Int) = pair
        val index = chainStartIndex + i
        ChainEntry(index, refForCell(c), MultihashReference.forDataObject(c), c.chain)
      }

    canonicalEntries ++ chainEntries
  }
}
