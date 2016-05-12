package io.mediachain.client

import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.JournalListener

import scala.collection.immutable.SortedMap

class InMemoryJournalFollower(val datastore: Datastore) extends JournalListener {

  override def onJournalCommit(entry: JournalEntry): Unit = {
    // TODO
  }

  override def onJournalBlock(ref: Reference): Unit =
    datastore.getAs[JournalBlock](ref).foreach { block =>
      handleNewBlock(ref, block)
    }


  private var blockRefMap: SortedMap[BigInt, Reference] = SortedMap()

  def blocks: List[JournalBlock] =
    blockRefMap.values.toList.flatMap(datastore.getAs[JournalBlock])

  def currentBlock: Option[JournalBlock] =
    blockRefMap
      .lastOption
      .flatMap(pair => datastore.getAs[JournalBlock](pair._2))

  // Map of canonical reference to most recent ChainEntry
  // for that canonical
  private var chainHeads: Map[Reference, ChainEntry] = Map()

  /// Set of all `CanonicalRecord`s referenced in the blockchain
  def canonicals: Set[CanonicalRecord] =
    blocks.toSet.flatMap { block: JournalBlock =>
      val refs = block.entries.collect {
       case e: CanonicalEntry => e.ref
      }
      refs.flatMap(datastore.getAs[CanonicalRecord])
    }


  /// Set of all chain cells in the block chain.
  def chainCells: Set[ChainCell] =
    blocks.toSet.flatMap { block: JournalBlock =>
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
    *
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
      chainHeads += (entry.ref -> latest)
    }
  }


  private def catchupPriorBlocks(block: JournalBlock): Unit = {
    for {
      prevBlockRef <- block.chain
      prevBlock <- datastore.getAs[JournalBlock](prevBlockRef)
    } yield {
      if (!blockRefMap.contains(prevBlock.index)) {
        handleNewBlock(prevBlockRef, prevBlock)
      }
    }
  }


  private def handleNewBlock(blockRef: Reference, block: JournalBlock): Unit = {
    blockRefMap += (block.index -> blockRef)
    val chainEntries = block.entries.toSeq
      .collect { case e: ChainEntry => e }

    updateChainHeads(chainEntries)
    catchupPriorBlocks(block)
  }


}
