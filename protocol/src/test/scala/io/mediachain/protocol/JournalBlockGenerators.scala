package io.mediachain.protocol

object JournalBlockGenerators {
  import io.mediachain.protocol.Datastore._
  import org.scalacheck._
  import DataObjectGenerators._


  /**
    * Generate a chain of cells for the given `CanonicalRecord`.
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
    val cellGen: Gen[ChainCell] = canonical match {
      case e: Entity => {
        val entityGen = Gen.const(e)
        Gen.oneOf(
          genEntityUpdateCell(entityGen),
          genEntityLinkCell(entityGen)
        )
      }

      case a: Artefact => {
        val artefactGen = Gen.const(a)
        Gen.oneOf(
          genArtefactUpdateCell(artefactGen),
          genArtefactCreationCell(artefactGen),
          genArtefactDerivationCell(artefactGen),
          genArtefactOwnershipCell(artefactGen),
          genArtefactReferenceCell(artefactGen)
        )
      }
    }

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
