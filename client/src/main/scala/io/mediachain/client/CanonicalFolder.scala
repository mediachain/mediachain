package io.mediachain.client

import io.mediachain.protocol.Datastore._

class CanonicalFolder(datastore: Datastore) {
  import io.mediachain.util.cbor.CborAST.CValue

  /**
    * Tries to extend the list of `cells` by following the `chain`
    * reference of the cell at the head of the list.  If successful,
    * prepends the new cell to the head of the list and repeats until
    * a nil `chain` reference is found, or the referenced cell can't
    * be retrieved from the datastore.
    *
    * @param cells - a list of `ChainCell`s to extend
    * @tparam T - Should be either EntityChainCell or ArtefactChainCell -
    *           using a concrete subtype may fail to find all cells.
    * @return - all the `ChainCell`s that can be found by chasing
    *         `chain` references from the head of the input list
    */
  def extendChain[T <: ChainCell](cells: List[T]): List[T] = {
    val nextCellRef = cells.headOption.flatMap(_.chain)
    val nextCellOpt = nextCellRef.flatMap(datastore.getAs[T])
    if (nextCellOpt.isEmpty) {
      cells
    } else {
      extendChain(nextCellOpt.toList ++ cells)
    }
  }

  /**
    * Given a list of `ChainCell`s, return a folded `meta` map containing
    * each cell's `meta` map applied in sequence.
    * @param chain - a list of `ChainCell`s to merge
    * @return a flattened `meta` map
    */
  def foldedMeta(chain: List[ChainCell]): Map[String, CValue] =
    // FIXME: this is very naive and doesn't flag or otherwise resolve conflicts
    chain.foldLeft(Map[String, CValue]()) { (meta, cell) =>
      meta ++ cell.meta
    }

  /**
    * Given an `Entity` and the head of its chain, return a "folded"
    * representation of the `Entity`
    * @param entity - the canonical `Entity`
    * @param chainHead - the head of the `Entity`'s chain of update cells
    * @return - a new `Entity` with the metadata from each cell merged in
    */
  def foldedEntity(entity: Entity, chainHead: Option[EntityChainCell]): Entity = {
    val chain = extendChain(chainHead.toList)
    Entity(entity.meta ++ foldedMeta(chain))
  }


  /**
    * Given an `Artefact` and the head of its chain, return a "folded"
    * representation of the `Artefact`
    * @param artefact - the canonical `Artefact`
    * @param chainHead - the head of the `Artefact`'s chain of update cells
    * @return - a new `Artefact` with the metadata from each cell merged in
    */
  def foldedArtefact(artefact: Artefact, chainHead: Option[ArtefactChainCell]): Artefact = {
    val chain = extendChain(chainHead.toList)
    Artefact(artefact.meta ++ foldedMeta(chain))
  }

  /**
    * Given an `CanonicalRecord` and the head of its chain, return a "folded"
    * representation of the `CanonicalRecord`
    * @param canonical - the canonical record
    * @param chainHead - the head of the `CanonicalRecord`'s chain of update cells
    * @return - a new `CanonicalRecord` with the metadata from each cell merged in
    * @throws IllegalStateException if given a `ChainCell` of the incorrect
    *                               type for the given `CanonicalRecord`, e.g.
    *                               if given an `Entity` and an `ArtefactUpdateCell`
    *
    */
  def foldedCanonical(canonical: CanonicalRecord, chainHead: Option[ChainCell]): CanonicalRecord = {
    (canonical, chainHead) match {
      case (e: Entity, None) => e
      case (a: Artefact, None) => a

      case (e: Entity, Some(c: EntityChainCell)) =>
        foldedEntity(e, Some(c))

      case (a: Artefact, Some(c: ArtefactChainCell)) =>
        foldedArtefact(a, Some(c))

      case _ =>
        throw new IllegalStateException(
          s"type mismatch between canonical of type ${canonical.getClass.getTypeName}" +
            s" and cell of type ${chainHead.get.getClass.getTypeName}"
        )
    }
  }
}
