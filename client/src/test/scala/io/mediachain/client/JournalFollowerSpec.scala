package io.mediachain.client

import io.mediachain.BaseSpec
import org.specs2.ScalaCheck
import org.specs2.matcher.ContainWithResultSeq

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

       given a bootstrapped JournalFollower:
         - updates itself when a new block is added $updatesWithNewBlock
      """

  implicit val scalaCheckParams = Parameters(
    minTestsOk = 10,
    maxSize = 5
  )

  private def bootstrappedFollower(chainWithStore: BlockChainWithDatastore): InMemoryJournalFollower = {
    val follower = new InMemoryJournalFollower(chainWithStore.datastore)

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

  def updatesWithNewBlock = prop { chainWithStore: BlockChainWithDatastore =>
    val follower = bootstrappedFollower(chainWithStore)
    val newBlockWithStore =
      genJournalBlock(5, chainWithStore.datastore, chainWithStore.blockChain.lastOption)
      .sample.getOrElse(
        throw new Exception("Unable to generate new block for mock mediachain")
      )

    // update the follower's datastore.  In a real-world scenario with a
    // shared global datastore, this would be unnecessary
    newBlockWithStore.datastore.store.foreach { pair =>
      follower.datastore.put(pair._2)
    }

    val newBlockRef = MultihashReference.forDataObject(newBlockWithStore.block)
    follower.onJournalBlock(newBlockRef)

    val canonicalRefsInNewBlock = newBlockWithStore.block.entries.collect {
      case e: CanonicalEntry => e.ref
    }
    val canonicalsInNewBlock = canonicalRefsInNewBlock.flatMap(
      newBlockWithStore.datastore.getAs[CanonicalRecord]
    )

    canonicalsInNewBlock.length must_== canonicalRefsInNewBlock.length
    follower.currentBlock must_== Some(newBlockWithStore.block)
    follower.canonicals must containAllOf(canonicalsInNewBlock)
  }
}
