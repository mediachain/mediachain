package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}

import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import io.mediachain.types.DatastoreTypes._
import io.mediachain.types.TransactorTypes._

object JournalCommitSpec extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
  val timeout = Duration(5, TimeUnit.SECONDS)

  def is =
    sequential ^
  s2"""
  JournalStateMachine publishes commit events:
   - when inserting an entity $insertEntity
   - when extending the entity chain $extendEntityChain
   - when extending the entity chain further $extendEntityChainFurther
   - when inserting an artefact $insertArtefact
   - when extending the artefact chain $extendArtefactChain
  """

  def beforeAll() {
    JournalCommitSpecContext.setup()
  }
  
  def afterAll() {
    JournalCommitSpecContext.shutdown()
  }
  
  def insertEntity = {
    val context = JournalCommitSpecContext.context
    val op = context.dummy.client.insert(Entity(Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: CanonicalEntry) => {context.ref = entry.ref})
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: CanonicalEntry) =>
      entry must_== xentry
    }
  }
  
  def extendEntityChain = {
    val context = JournalCommitSpecContext.context
    val ref = context.ref
    val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: ChainEntry) =>
      entry must_== xentry
    }
  }
  
  def extendEntityChainFurther = {
    val context = JournalCommitSpecContext.context
    val ref = context.ref
    val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: ChainEntry) =>
      entry must_== xentry
    }
  }
  
  def insertArtefact = {
    val context = JournalCommitSpecContext.context
    val op = context.dummy.client.insert(Artefact(Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: CanonicalEntry) => {context.ref = entry.ref})
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: CanonicalEntry) =>
      entry must_== xentry
    }
  }
  
  def extendArtefactChain = {
    val context = JournalCommitSpecContext.context
    val ref = context.ref
    val op = context.dummy.client.update(ref, ArtefactChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: ChainEntry) =>
      entry must_== xentry
    }
  }
}

class JournalCommitSpecContext(val dummy: DummyContext, 
                               val qclient: Copycat.Client, 
                               val queue: BlockingQueue[JournalEntry]) {
  var ref: Reference = null
}

object JournalCommitSpecContext {
  var instance: JournalCommitSpecContext = null
  def setup(): Unit = {
    val dummy = DummyContext.setup("127.0.0.1:10001")
    val qclient = Copycat.Client.build()
    val queue = new LinkedBlockingQueue[JournalEntry]
    qclient.connect("127.0.0.1:10001")
    qclient.listen(new JournalListener {
      def onJournalCommit(entry: JournalEntry) {
        queue.offer(entry)
      }
      def onJournalBlock(ref: Reference) {}
    })
    instance = new JournalCommitSpecContext(dummy, qclient, queue)
  }
  
  def shutdown(): Unit = {
    instance.qclient.close()
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}
