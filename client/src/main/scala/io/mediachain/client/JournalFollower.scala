package io.mediachain.client

import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.JournalListener

class JournalFollower(datastore: Datastore) extends JournalListener {
  import collection.mutable.{Set => MSet}

  private val blockSet: MSet[JournalBlock] = MSet()
  def blocks = blockSet.toList.sortBy(_.index)
  def currentBlock: Option[JournalBlock] = blocks.lastOption

  def canonicals: Set[CanonicalRecord] =
    blockSet.toSet.flatMap { block: JournalBlock =>
      val refs = block.entries.collect {
        case e: CanonicalEntry => e.ref
      }
      refs.flatMap(getCanonical)
    }


  def chainCells: Set[ChainCell] =
    blockSet.toSet.flatMap { block: JournalBlock =>
      val refs = block.entries.collect {
        case e: ChainEntry => e.chain
      }
      refs.flatMap(getChainCell)
    }

  def bootstrapJournal(currentBlockRef: Reference): Unit =
    onJournalBlock(currentBlockRef)

  def getBlock(ref: Reference): Option[JournalBlock] =
    datastore.get(ref).collect { case b: JournalBlock => b }

  def getCanonical(ref: Reference): Option[CanonicalRecord] =
    datastore.get(ref).collect { case c: CanonicalRecord => c}

  def getChainCell(ref: Reference): Option[ChainCell] =
    datastore.get(ref).collect { case c: ChainCell => c}

  private def catchupPriorBlocks(currentBlock: JournalBlock): Unit = {
    for {
      prevRef <- currentBlock.chain
      prevBlock <- getBlock(prevRef)
    } yield {
      if (!blockSet.contains(prevBlock)) {
        blockSet.add(prevBlock)
        catchupPriorBlocks(prevBlock)
      }
    }
  }

  override def onJournalCommit(entry: JournalEntry): Unit = {

  }

  override def onJournalBlock(ref: Reference): Unit = {
    val blockOpt: Option[JournalBlock] = getBlock(ref)

    blockOpt.foreach { block =>
      blockSet.add(block)
      catchupPriorBlocks(block)
    }
  }
}
