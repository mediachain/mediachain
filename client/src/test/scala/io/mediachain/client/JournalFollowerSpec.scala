package io.mediachain.client

import io.mediachain.BaseSpec
import org.specs2.ScalaCheck

object JournalFollowerSpec extends BaseSpec with ScalaCheck {
  import io.mediachain.protocol.Datastore._
  import io.mediachain.protocol.JournalBlockGenerators._
  import org.specs2.scalacheck.Parameters


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

  private def bootstrappedFollower(chainWithStore: BlockChainWithDatastore): JournalFollower = {
    val follower = new JournalFollower(chainWithStore.datastore)

    val chainHead = chainWithStore.blockChain.lastOption
    val chainHeadRef = chainHead.map(MultihashReference.forDataObject)
    chainHeadRef.foreach(follower.bootstrapJournal)
    follower
  }

  def fetchesAllBlocks = prop { chainWithStore: BlockChainWithDatastore =>
    val follower = bootstrappedFollower(chainWithStore)

    follower.currentBlock must_== chainWithStore.blockChain.lastOption
    follower.blocks must containTheSameElementsAs(chainWithStore.blockChain)
  }

  def fetchesAllCanonicals = prop { chainWithStore: BlockChainWithDatastore =>
    val allCanonicals = chainWithStore.datastore.store.values.collect {
      case c: CanonicalRecord => c
    }.toList

    val follower = bootstrappedFollower(chainWithStore)
    follower.canonicals must containTheSameElementsAs(allCanonicals)
  }

  def fetchesAllChainCells = prop { chainWithStore: BlockChainWithDatastore =>
    val allCells = chainWithStore.datastore.store.values.collect {
      case c: ChainCell => c
    }.toList

    val follower = bootstrappedFollower(chainWithStore)
    follower.chainCells must containTheSameElementsAs(allCells)
  }
}
