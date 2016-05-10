package io.mediachain.protocol

object JournalBlockGenerators {
  import io.mediachain.protocol.Datastore._
  import org.scalacheck._
  import DataObjectGenerators._


  /**
    * Make a generator that returns a chain cell for the given `CanonicalRecord`
    *
    * @param canonical either an `Entity` or `Artefact` to generate a cell for
    * @return depending on the type of `CanonicalRecord` given, a generator for
    *         either a subtype of `EntityChainCell` or `EntityArtefactCell`.
    *         Note that the `chain` field of the generated cell will be
    *         set to `None`
    *
    */
  def genCellFor(canonical: CanonicalRecord)
  : Gen[ChainCell] =
    canonical match {
      case e: Entity => genEntityUpdateCell(e, genNilReference)
      case a: Artefact => genArtefactUpdateCell(a, genNilReference)
    }


  /**
    * Takes a list of `ChainCell`s with nil `chain` references,
    * and joins them together so that cells which refer to the same
    * `CanonicalRecord` are "consed" together via their `chain` references.
    *
    * @param cells a list of `ChainCell`s with nil `chain` references
    * @return the input list, with the `chain` field of each cell updated
    *         so that it points to the previous cell that refers to the same
    *         `CanonicalRecord`.
    */
  def consChainCells(cells: List[ChainCell]): List[ChainCell] = {
    import collection.mutable.{Map => MMap, MutableList => MList}
    val chainHeads: MMap[Reference, ChainCell] = MMap()
    val consed: MList[ChainCell] = MList()

    cells.foreach { c =>
      val canonicalRef = c.ref
      val head = chainHeads.get(canonicalRef)
        .map(h => MultihashReference.forDataObject(h))

      val consedCell = c.cons(head)
      chainHeads.put(canonicalRef, consedCell)
      consed += consedCell
    }

    consed.toList
  }


  /**
    * Makes a generator that generates a `JournalBlock` and mock `Datastore`
    * (as a `Map[Reference, DataObject]`).
    *
    * The output of the returned generator can be fed back into this function
    * to create a generator for the next block.  The resulting mock `Datastore`
    * will contain all of the generated blocks, as well as the `DataObject`s
    * refered to in each block.
    *
    * @param blockSize - number of journal entries per block
    * @param datastore - an in-memory datastore containing any previously
    *                  generated objects and journal blocks
    * @param blockchain the head of a previously generated journal blockchain
    * @return a generator of the new head of a journal blockchain, and an
    *         in-memory datastore containing all generated objects (including
    *         journal blocks)
    */
  def genJournalBlock(
    blockSize: Int,
    datastore: InMemoryDatastore,
    blockchain: Option[JournalBlock]
  ): Gen[(JournalBlock, InMemoryDatastore)] = {
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

      cellGen = Gen.oneOf(canonicals)
        .flatMap(genCellFor(_))

      chainCells <- Gen.listOfN(numChainCells, cellGen).map(consChainCells)
    } yield {
      val startIndex: BigInt = blockchain.map(_.index).getOrElse(0)
      val entries = toJournalEntries(startIndex, canonicals, chainCells)
      val blockIndex = entries.lastOption.map(_.index + 1).getOrElse(startIndex)

      val prevBlockRef = blockchain.map(MultihashReference.forDataObject)
      val block = JournalBlock(blockIndex, prevBlockRef, entries.toArray)

      val generatedObjects = block :: (canonicals ++ chainCells)

      val updatedStore = datastore.copy
      generatedObjects.foreach(updatedStore.put)
      (block, updatedStore)
    }
  }


  /**
    * Map DataObjects to JournalEntries.
    * @param startIndex index of first entry
    * @param canonicals generated `CanonicalRecord`s
    * @param chainCells generated `ChainCell`s
    * @return list of `JournalEntry` objects for each record
    */
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
        ChainEntry(index, c.ref, MultihashReference.forDataObject(c), c.chain)
      }

    canonicalEntries ++ chainEntries
  }
}
