package io.mediachain.client

import io.mediachain.BaseSpec
import io.mediachain.protocol.InMemoryDatastore
import org.specs2.ScalaCheck
import org.specs2.scalacheck.Parameters

object JournalFollowerSpec extends BaseSpec with ScalaCheck {
  import io.mediachain.protocol.Datastore._
  import io.mediachain.protocol.JournalBlockGenerators._

  def is =
    s2"""
        given the current journal block and a populated datastore:
          - fetches all previous blocks from datastore $fetchesAllBlocks
          - fetches all canonical records $fetchesAllCanonicals
          - fetches all chain cells $fetchesAllChainCells
      """

  implicit val scalaCheckParams = Parameters(
    minTestsOk = 10,
    maxSize = 5
  )


  def fetchesAllBlocks = prop { chainWithStore: BlockChainWithDatastore =>
    val follower = new JournalFollower(chainWithStore.datastore)

    val chainHead = chainWithStore.blockChain.lastOption
    val chainHeadRef = chainHead.map(MultihashReference.forDataObject)
    chainHeadRef.foreach(follower.bootstrapJournal)


    follower.currentBlock must_== chainHead

    follower.sortedBlocks must containTheSameElementsAs(chainWithStore.blockChain)
  }

  def fetchesAllCanonicals = pending

  def fetchesAllChainCells = pending
}
