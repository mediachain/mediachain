package io.mediachain.client

import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor.JournalListener

class JournalFollower(datastore: Datastore) extends JournalListener {
  import collection.mutable.{Set => MSet}

  private val blocks: MSet[JournalBlock] = MSet()
  def sortedBlocks = blocks.toList.sortBy(_.index)

  def currentBlock: Option[JournalBlock] = sortedBlocks.lastOption

  def bootstrapJournal(currentBlockRef: Reference): Unit = {
    val blockOpt: Option[JournalBlock] = getBlock(currentBlockRef)

    blockOpt.foreach { block =>
      blocks.add(block)
      catchupPriorBlocks(block)
    }
  }

  private def getBlock(ref: Reference): Option[JournalBlock] =
    datastore.get(ref).collect { case b: JournalBlock => b }

  private def catchupPriorBlocks(currentBlock: JournalBlock): Unit = {
    for {
      prevRef <- currentBlock.chain
      prevBlock <- getBlock(prevRef)
    } yield {
      blocks.add(prevBlock)
      catchupPriorBlocks(prevBlock)
    }
  }

  override def onJournalCommit(entry: JournalEntry): Unit = {

  }

  override def onJournalBlock(ref: Reference): Unit = {

  }
}
