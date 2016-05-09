package io.mediachain.protocol

object JournalBlockGenerators {
  import io.mediachain.protocol.Datastore._
  import org.scalacheck._
  import DataObjectGenerators._


  /**
    * Generate a chain cell that references the given `CanonicalRecord`
    *
    * @param canonical either an `Entity` or `Artefact` to generate a cell for
    * @return depending on the type of `CanonicalRecord` given, either a
    *         subtype of `EntityChainCell` or `EntityArtefactCell`.
    *         Note that the `chain` field of the returned cell will be
    *         set to `None`
    *
    */
  def genCellFor(canonical: CanonicalRecord): Gen[ChainCell] = {
    canonical match {
      case e: Entity => {
        val entityGen = Gen.const(e)
        Gen.oneOf(
          genEntityUpdateCell(entityGen, genNilReference),
          genEntityLinkCell(entityGen, genNilReference)
        )
      }

      case a: Artefact => {
        val artefactGen = Gen.const(a)
        Gen.oneOf(
          genArtefactUpdateCell(artefactGen, genNilReference),
          genArtefactCreationCell(artefactGen, genNilReference),
          genArtefactDerivationCell(artefactGen, genNilReference),
          genArtefactOwnershipCell(artefactGen, genNilReference),
          genArtefactReferenceCell(artefactGen, genNilReference)
        )
      }
    }
  }


  /**
    * Generate a chain of cells for the given `CanonicalRecord`.
    *
    * @param length number of cells to generate
    * @param canonical the `ref` for each cell.  The type of `CanonicalRecord`
    *                  given will determine whether `EntityChainCell`s or
    *                  `ArtefactChainCell`s are generated
    * @return - a generator that returns a `List[ChainCell]` of the given
    *         `length`, whose cells are consed together so that the head
    *         of the list has a `chain` that points to the next cell, and
    *         so on. The last cell in the list will have a `chain` value of
    *         `None`.
    */
  def genChain(length: Int, canonical: CanonicalRecord): Gen[List[ChainCell]] = {
    val cellGen: Gen[ChainCell] = genCellFor(canonical)

    for {
      cells <- Gen.listOfN(length, cellGen)
    } yield {
      cells.foldLeft(List[ChainCell]()) { (consedCells, cell) =>
        val headRef = consedCells.headOption
          .map(c => MultihashReference.forDataObject(c))

        cell.cons(headRef) :: consedCells
      }
    }
  }
}
