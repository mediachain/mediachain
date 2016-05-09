package io.mediachain.protocol

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


  def consChainCells(cells: List[ChainCell]): List[ChainCell] = {
    import collection.mutable.{Map => MMap, MutableList => MList}
    val chainHeads: MMap[Reference, ChainCell] = MMap()
    val consed: MList[ChainCell] = MList()

    cells.foreach { c =>
      val canonicalRef = c match {
        case e: EntityChainCell => e.entity
        case a: ArtefactChainCell => a.artefact
      }

      val head = chainHeads.get(canonicalRef)
        .map(h => MultihashReference.forDataObject(h))

      val consedCell = c.cons(head)
      chainHeads.put(canonicalRef, consedCell)
      consed += consedCell
    }

    consed.toList
  }


  type MockMediachain = (Map[MultihashReference, DataObject], List[JournalBlock])

  /**
    * Generates a mock `Datastore` (as a `Map[MultihashReference, DataObject]`)
    * and a journal, (as a `List[JournalBlock]`).
    *
    * @param length number of `JournalBlock`s to generate
    * @param blockSize number of `JournalEntries` per block
    * @return a tuple containing the mock datastore and the list of journal
    *         blocks.
    */

  def genMediachain(length: Int, blockSize: Int)
  : Gen[MockMediachain] = {
    // the idea here is to generate all the data objects up front, then map
    // them each into a `JournalEntry` and bundle those up into blocks until
    // we have enough.

    val numDataObjects = length * blockSize

    // generate ~ twice as many chain cells as canonical entries
    val numCanonicals = (numDataObjects * 0.3).toInt
    val numChainCells = numDataObjects - numCanonicals

    // generate ~ 3x as many artefacts as entities
    val numEntities = (numCanonicals * 0.25).toInt
    val numArtefacts = numCanonicals - numEntities

    for {
      entities <- Gen.listOfN(numEntities, genEntity)
      artefacts <- Gen.listOfN(numArtefacts, genArtefact)

      canonicals = entities ++ artefacts
      entityReferences = entities.map(e => MultihashReference.forDataObject(e))
      artefactReferences = artefacts.map(a => MultihashReference.forDataObject(a))

      cellGen = Gen.oneOf(canonicals)
        .flatMap(genCellFor(_, entityReferences, artefactReferences))

      chainCells <- Gen.listOfN(numChainCells, cellGen)
      consed = consChainCells(chainCells)
    } yield {

      ???
    }
  }


  private def toJournalEntries(canonicals: List[CanonicalRecord], chainCells: List[ChainCell]):
    List[JournalEntry] = {
    import collection.mutable.{MutableList => MList}
    val mCanonicals = MList(canonicals)
    val mCells = MList(chainCells)


    val entries: MList[JournalEntry] = MList()


    ???
  }
}
