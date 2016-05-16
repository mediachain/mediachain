package io.mediachain.copycat

import org.specs2.specification.{AfterAll, BeforeAll}

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.collection.mutable.ListBuffer

import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor._
import StateMachine.JournalBlockSize

object JournalBlockchainSpec2 extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
  val timeout = Duration(120, TimeUnit.SECONDS)
  
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
  
  def generateBlock = skipped {
    val context = JournalBlockchainSpec2Context.context
    val op = context.dummy.client.insert(Entity(Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: CanonicalEntry) => {
      val ref = entry.ref
      for (_ <- 1 to (JournalBlockSize - 1)) {
        context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
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
    dummy.client.listen(new JournalListener {
      def onJournalBlock(ref: Reference) {
          queue.offer(ref)
      }
      def onJournalCommit(entry: JournalEntry) {}
    })
    instance = new JournalBlockchainSpec2Context(dummy, queue)
  }
  
  def shutdown(): Unit = {
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}
