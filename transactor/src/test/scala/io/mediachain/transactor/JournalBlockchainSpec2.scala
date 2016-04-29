package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}

import java.util.function.Consumer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import scala.collection.mutable.ListBuffer

import Types._
import StateMachine._

object JournalBlockchainSpec2 extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
  def is =
  s2"""
  JournalStateMachine generates Journal blocks
   - it generates a full block as quickly as possible $generateBlock
  """
  
  def beforeAll() {
    JournalBlockchainSpec2Context.setup()
  }
  
  def afterAll() {
    JournalBlockchainSpec2Context.shutdown()
  }
  
  def generateBlock = {
    val context = JournalBlockchainSpec2Context.context
    val res = context.dummy.client.submit(JournalInsert(Entity(Map()))).join()
    res.foreach((entry: CanonicalEntry) => {
      val ref = entry.ref
      for (x <- 1 to (JournalBlockSize - 1)) {
        context.dummy.client.submit(JournalUpdate(ref, EntityChainCell(ref, None, Map())))
      }})
    val blockref = context.queue.poll(300, TimeUnit.SECONDS)
    val block = context.dummy.store.get(blockref)
    (block must beSome) and
    (block.get.asInstanceOf[JournalBlock].index must_== JournalBlockSize)
  }
}

class JournalBlockchainSpec2Context(val dummy: DummyContext,
                                    val queue: BlockingQueue[Reference])

object JournalBlockchainSpec2Context {
  var instance: JournalBlockchainSpec2Context = null

  def setup(): Unit = {
    val dummy = DummyContext.setup("127.0.0.1:10003")
    val queue = new LinkedBlockingQueue[Reference]
    dummy.client.onEvent("journal-block", 
      new Consumer[JournalBlockEvent] { 
        def accept(evt: JournalBlockEvent) { 
          queue.offer(evt.ref)
        }
    })
    instance = new JournalBlockchainSpec2Context(dummy, queue)
  }
  
  def shutdown(): Unit = {
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}
