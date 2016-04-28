package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}

import java.util.function.Consumer
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import io.atomix.copycat.client.CopycatClient
import io.atomix.catalyst.transport.Address

import Types._
import StateMachine._

object JournalCommitSpec extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{

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
    val res = context.dummy.client.submit(JournalInsert(Entity(Map()))).join()
    res.foreach((entry: CanonicalEntry) => {context.ref = entry.ref})
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: CanonicalEntry) =>
      entry must_== xentry
    }
  }
  
  def extendEntityChain = {
    val context = JournalCommitSpecContext.context
    val ref = context.ref
    val res = context.dummy.client.submit(JournalUpdate(ref, EntityChainCell(ref, None, Map()))).join()
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: ChainEntry) =>
      entry must_== xentry
    }
  }
  
  def extendEntityChainFurther = {
    val context = JournalCommitSpecContext.context
    val ref = context.ref
    val res = context.dummy.client.submit(JournalUpdate(ref, EntityChainCell(ref, None, Map()))).join()
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: ChainEntry) =>
      entry must_== xentry
    }
  }
  
  def insertArtefact = {
    val context = JournalCommitSpecContext.context
    val res = context.dummy.client.submit(JournalInsert(Artefact(Map()))).join()
    res.foreach((entry: CanonicalEntry) => {context.ref = entry.ref})
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: CanonicalEntry) =>
      entry must_== xentry
    }
  }
  
  def extendArtefactChain = {
    val context = JournalCommitSpecContext.context
    val ref = context.ref
    val res = context.dummy.client.submit(JournalUpdate(ref, ArtefactChainCell(ref, None, Map()))).join()
    val entry = context.queue.poll(1, TimeUnit.SECONDS)
    res must beRightXor { (xentry: ChainEntry) =>
      entry must_== xentry
    }
  }
}

class JournalCommitSpecContext(val dummy: DummyContext, 
                               val qclient: CopycatClient, 
                               val queue: BlockingQueue[JournalEntry]) {
  var ref: Reference = null
}

object JournalCommitSpecContext {
  var instance: JournalCommitSpecContext = null
  def setup() {
    val dummy = DummyContext.setup("127.0.0.1:10001", "/tmp/transactor-test/commit")
    val qclient = Copycat.Client.build()
    val queue = new LinkedBlockingQueue[JournalEntry]
    qclient.connect(new Address("127.0.0.1:10001")).join()
    qclient.onEvent("journal-commit", 
      new Consumer[JournalCommitEvent] { 
        def accept(evt: JournalCommitEvent) { 
          queue.offer(evt.entry)
        }
    })
    instance = new JournalCommitSpecContext(dummy, qclient, queue)
  }
  
  def shutdown() {
    instance.qclient.close().join()
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}
