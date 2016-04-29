package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}

import java.util.function.Consumer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import scala.collection.mutable.ListBuffer

import Types._
import StateMachine._

object JournalBlockchainSpec extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
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
    val block = context.dummy.client.submit(JournalCurrentBlock()).join()
    (block.index must_== 0) and
    (block.chain must beNone) and
    (block.entries must beEmpty)
  }
  
  def accumulateBlock = {
    val context = JournalBlockchainSpecContext.context
    val res = context.dummy.client.submit(JournalInsert(Entity(Map()))).join()
    res.foreach((entry: CanonicalEntry) => {
      context.entries += entry
      context.entityRef = entry.ref
    })
    val ref = context.entityRef
    for (x <- 1 to 5) {
      val res = context.dummy.client.submit(JournalUpdate(ref, EntityChainCell(ref, None, Map()))).join()
      res.foreach((entry: ChainEntry) => {context.entries += entry})
    }
    
    val block = context.dummy.client.submit(JournalCurrentBlock()).join()
    (block.index must_== 6) and
    (block.chain must beNone) and
    (block.entries must_== context.entries.toArray)
  }
  
  def generateBlock = {
    val context = JournalBlockchainSpecContext.context
    val ref = context.entityRef
    for (x <- 6 to (JournalBlockchainSpecContext.BlockSize - 1)) {
      val res = context.dummy.client.submit(JournalUpdate(ref, EntityChainCell(ref, None, Map()))).join()
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
    val block = context.dummy.client.submit(JournalCurrentBlock()).join()
    (block.index must_== JournalBlockchainSpecContext.BlockSize) and
    (block.chain must_== Some(context.blockRef)) and
    (block.entries must beEmpty)
  }
  
  def generateAnotherBlock = {
    val context = JournalBlockchainSpecContext.context
    val ref = context.entityRef
    context.entries = new ListBuffer
    for (x <- 1 to JournalBlockchainSpecContext.BlockSize) {
      val res = context.dummy.client.submit(JournalUpdate(ref, EntityChainCell(ref, None, Map()))).join()
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
    dummy.client.onEvent("journal-block", 
      new Consumer[JournalBlockEvent] { 
        def accept(evt: JournalBlockEvent) { 
          queue.offer(evt.ref)
        }
    })
    instance = new JournalBlockchainSpecContext(dummy, queue)
  }
  
  def shutdown(): Unit = {
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}


