package io.mediachain.client

import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.JournalListener

class JournalFollower(val datastore: Datastore) extends JournalListener {
  import collection.mutable.{Set => MSet, Map => MMap}

  override def onJournalCommit(entry: JournalEntry): Unit = {
    // TODO
  }

  override def onJournalBlock(ref: Reference): Unit =
    handleNewBlock(ref)


  private val blockRefs: MSet[Reference] = MSet()
  private def blockSet: Set[JournalBlock] =
    blockRefs.toSet.flatMap(datastore.getAs[JournalBlock])

  def blocks: List[JournalBlock] = blockSet.toList.sortBy(_.index)
  def currentBlock: Option[JournalBlock] = blocks.lastOption

  // Map of canonical reference to most recent ChainEntry
  // for that canonical
  private val chainHeads: MMap[Reference, ChainEntry] = MMap()

  /// Set of all `CanonicalRecord`s referenced in the blockchain
  def canonicals: Set[CanonicalRecord] =
    blockSet.flatMap { block: JournalBlock =>
      val refs = block.entries.collect {
       case e: CanonicalEntry => e.ref
      }
      refs.flatMap(datastore.getAs[CanonicalRecord])
    }


  /// Set of all chain cells in the block chain.
  def chainCells: Set[ChainCell] =
    blockSet.flatMap { block: JournalBlock =>
      val refs = block.entries.collect {
        case e: ChainEntry => e.chain
      }
      refs.flatMap(datastore.getAs[ChainCell])
    }


  /** reconstruct the blockchain from a reference to the current block
    *
    * @param currentBlockRef reference to the most recently committed
    *                        `JournalBlock`
    */
  def bootstrapJournal(currentBlockRef: Reference): Unit =
    onJournalBlock(currentBlockRef)


  /**
    * Get the most recent `ChainCell` that's been applied to the referenced
    * `CanonicalRecord`.
    * @param canonicalRef reference to a `CanonicalRecord`
    * @return the most recently applied `ChainCell`, or None if none exist
    */
  def getChainHeadForCanonical(canonicalRef: Reference): Option[ChainCell] =
    chainHeads.get(canonicalRef)
      .flatMap(entry => datastore.getAs[ChainCell](entry.chain))


  private def updateChainHeads(chainEntries: Seq[ChainEntry]): Unit = {
    chainEntries.foreach { entry =>
      val latest = chainHeads.get(entry.ref) match {
        case Some(existing) if existing.index >= entry.index => existing
        case _ => entry
      }
      chainHeads.put(entry.ref, latest)
    }
  }


  private def catchupPriorBlocks(block: JournalBlock): Unit = {
    block.chain
      .foreach { prevBlockRef =>
        if (!blockRefs.contains(prevBlockRef)) {
          handleNewBlock(prevBlockRef)
        }
      }
  }


  private def handleNewBlock(blockRef: Reference): Unit = {
    datastore.getAs[JournalBlock](blockRef).foreach { block =>
      blockRefs.add(blockRef)
      val chainEntries = block.entries.toSeq
        .collect { case e: ChainEntry => e }

      updateChainHeads(chainEntries)
      catchupPriorBlocks(block)
    }
  }


}
