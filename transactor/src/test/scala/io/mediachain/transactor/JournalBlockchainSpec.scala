package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.collection.mutable.ListBuffer

import Types._
import StateMachine.JournalBlockSize

object JournalBlockchainSpec extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
  val timeout = Duration(5, TimeUnit.SECONDS)
  
  def is =
    sequential ^
  s2"""
  JournalStateMachine generates and tracks the blockchain:
   - it starts with an empty block $hasEmptyBlock
   - it accumulates journal entries in the current block $accumulateBlock
   - it generates a block after BlockSize entries $generateBlock
   - it has an empty block again $hasEmptyBlockAgain
   - it generates a second block chained to the first $generateAnotherBlock
  """

  def beforeAll() {
    JournalBlockchainSpecContext.setup()
  }
  
  def afterAll() {
    JournalBlockchainSpecContext.shutdown()
  }
  
  def hasEmptyBlock = {
    val context = JournalBlockchainSpecContext.context
    val bop = context.dummy.client.currentBlock
    val block = Await.result(bop, timeout)
    (block.index must_== 0) and
    (block.chain must beNone) and
    (block.entries must beEmpty)
  }
  
  def accumulateBlock = {
    val context = JournalBlockchainSpecContext.context
    val op = context.dummy.client.insert(Entity(Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: CanonicalEntry) => {
      context.entries += entry
      context.entityRef = entry.ref
    })
    val ref = context.entityRef
    for (_ <- 1 to 5) {
      val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
      val res = Await.result(op, timeout)
      res.foreach((entry: ChainEntry) => {context.entries += entry})
    }
    
    val bop = context.dummy.client.currentBlock
    val block = Await.result(bop, timeout)
    (block.index must_== 6) and
    (block.chain must beNone) and
    (block.entries must_== context.entries.toArray)
  }
  
  def generateBlock = {
    val context = JournalBlockchainSpecContext.context
    val ref = context.entityRef
    for (_ <- 6 to (JournalBlockchainSpecContext.BlockSize - 1)) {
      val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
      val res = Await.result(op, timeout)
      res.foreach((entry: ChainEntry) => {context.entries += entry})
    }
    context.blockRef = context.queue.poll(1, TimeUnit.SECONDS)
    val block = context.dummy.store.get(context.blockRef)
    (block must beSome) and
    (block.get.asInstanceOf[JournalBlock].index must_== JournalBlockchainSpecContext.BlockSize) and
    (block.get.asInstanceOf[JournalBlock].chain must beNone) and
    (block.get.asInstanceOf[JournalBlock].entries must_== context.entries.toArray)
  }
  
  def hasEmptyBlockAgain = {
    val context = JournalBlockchainSpecContext.context
    val bop = context.dummy.client.currentBlock
    val block = Await.result(bop, timeout)
    (block.index must_== JournalBlockchainSpecContext.BlockSize) and
    (block.chain must_== Some(context.blockRef)) and
    (block.entries must beEmpty)
  }
  
  def generateAnotherBlock = {
    val context = JournalBlockchainSpecContext.context
    val ref = context.entityRef
    context.entries = new ListBuffer
    for (_ <- 1 to JournalBlockchainSpecContext.BlockSize) {
      val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
      val res = Await.result(op, timeout)
      res.foreach((entry: ChainEntry) => {context.entries += entry})
    }
    val blockref = context.queue.poll(1, TimeUnit.SECONDS)
    val block = context.dummy.store.get(blockref)
    (block must beSome) and
    (block.get.asInstanceOf[JournalBlock].index must_== (2 * JournalBlockchainSpecContext.BlockSize)) and
    (block.get.asInstanceOf[JournalBlock].chain must_== Some(context.blockRef)) and
    (block.get.asInstanceOf[JournalBlock].entries must_== context.entries.toArray)
  }
}

class JournalBlockchainSpecContext(val dummy: DummyContext,
                                   val queue: BlockingQueue[Reference]) {
  var entries: ListBuffer[JournalEntry] = new ListBuffer
  var entityRef: Reference = null
  var blockRef: Reference = null
}

object JournalBlockchainSpecContext {
  val BlockSize = 20
  var instance: JournalBlockchainSpecContext = null
  
  def setup(): Unit = {
    val dummy = DummyContext.setup("127.0.0.1:10002", BlockSize)
    val queue = new LinkedBlockingQueue[Reference]
    dummy.client.listen(new JournalListener {
      def onJournalBlock(ref: Reference) {
          queue.offer(ref)
      }
      def onJournalCommit(entry: JournalEntry) {}
    })
    instance = new JournalBlockchainSpecContext(dummy, queue)
  }
  
  def shutdown(): Unit = {
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}


