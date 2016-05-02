package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

import Types._

object JournalIndexSpec extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
  
  val timeout = Duration(5, TimeUnit.SECONDS)
  
  def is =
    sequential ^
  s2"""
  JournalStateMachine maintains a consistent Journal Index:
   - inserts entity $insertEntity
   - resolves entity to empty chain $resolveEntityEmpty
   - extends entity chain $extendEntityChain
   - resolves entity chain $resolveEntityChain
   - extends entity chain further $extendEntityChainFurther
   - inserts artefact $insertArtefact
   - resolves artefact to empty chain $resolveArtefactEmpty
   - extends artefact chain $extendArtefactChain
   - resolves artefact chain $resolveArtefactChain
   - extends artefact chain further $extendArtefactChainFurther
   - does not allow ArtefactCells on Entity chain $checkEntityXCons
   - does not allow EntityChainCells on Artefact chain $checkArtefactXCons
  """
  
  def beforeAll() {
    JournalIndexSpecContext.setup()
  }
  
  def afterAll() {
    JournalIndexSpecContext.shutdown()
  }

  def insertEntity = {
    val context = JournalIndexSpecContext.context
    val op = context.dummy.client.insert(Entity(Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: CanonicalEntry) => {context.entityRef = entry.ref})
    res must beRightXor
  }
  
  def resolveEntityEmpty = {
    val context = JournalIndexSpecContext.context
    val ref = context.entityRef
    val op = context.dummy.client.lookup(ref)
    val res = Await.result(op, timeout)
    res must beNone
  }
  
  def extendEntityChain = {
    val context = JournalIndexSpecContext.context
    val ref = context.entityRef
    val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: ChainEntry) => {context.entityChainRef = entry.chain})
    res must beRightXor { (entry: ChainEntry) =>
      (entry.ref must_== ref) and 
      (entry.chainPrevious must beNone)
    }
  }
  
  def resolveEntityChain = {
    val context = JournalIndexSpecContext.context
    val ref = context.entityRef
    val chainRef = context.entityChainRef
    val op = context.dummy.client.lookup(ref)
    val res = Await.result(op, timeout)
    (res must beSome) and 
    (res.get must_== chainRef)
  }
  
  def extendEntityChainFurther = {
    val context = JournalIndexSpecContext.context
    val ref = context.entityRef
    val chainRef = context.entityChainRef
    val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: ChainEntry) => {context.entityChainRef = entry.chain})
    res must beRightXor { (entry: ChainEntry) =>
      (entry.ref must_== ref) and 
      (entry.chainPrevious must beSome) and
      (entry.chainPrevious.get must_== chainRef)
    }
  }
  
  def insertArtefact = {
    val context = JournalIndexSpecContext.context
    val op = context.dummy.client.insert(Artefact(Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: CanonicalEntry) => {context.artefactRef = entry.ref})
    res must beRightXor
  }

  def resolveArtefactEmpty = {
    val context = JournalIndexSpecContext.context
    val ref = context.artefactRef
    val op = context.dummy.client.lookup(ref)
    val res = Await.result(op, timeout)
    res must beNone
  }
  
  def extendArtefactChain = {
    val context = JournalIndexSpecContext.context
    val ref = context.artefactRef
    val op = context.dummy.client.update(ref, ArtefactChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: ChainEntry) => {context.artefactChainRef = entry.chain})
    res must beRightXor { (entry: ChainEntry) =>
      (entry.ref must_== ref) and 
      (entry.chainPrevious must beNone)
    }
  }
  
  def resolveArtefactChain = {
    val context = JournalIndexSpecContext.context
    val ref = context.artefactRef
    val chainRef = context.artefactChainRef
    val op = context.dummy.client.lookup(ref)
    val res = Await.result(op, timeout)
    (res must beSome) and 
    (res.get must_== chainRef)
  }

  def extendArtefactChainFurther = {
    val context = JournalIndexSpecContext.context
    val ref = context.artefactRef
    val chainRef = context.artefactChainRef
    val op = context.dummy.client.update(ref, ArtefactChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    res.foreach((entry: ChainEntry) => {context.artefactChainRef = entry.chain})
    res must beRightXor { (entry: ChainEntry) =>
      (entry.ref must_== ref) and 
      (entry.chainPrevious must beSome) and
      (entry.chainPrevious.get must_== chainRef)
    }
  }
  
  def checkEntityXCons = {
    val context = JournalIndexSpecContext.context
    val ref = context.entityRef
    val op = context.dummy.client.update(ref, ArtefactChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    res must beLeftXor
  }
  
  def checkArtefactXCons = {
    val context = JournalIndexSpecContext.context
    val ref = context.artefactRef
    val op = context.dummy.client.update(ref, EntityChainCell(ref, None, Map()))
    val res = Await.result(op, timeout)
    res must beLeftXor
  }
}

class JournalIndexSpecContext(val dummy: DummyContext) {
  var entityRef: Reference = null
  var entityChainRef: Reference = null
  var artefactRef: Reference = null
  var artefactChainRef: Reference = null
}

object JournalIndexSpecContext {
  var instance: JournalIndexSpecContext = null
  
  def setup(): Unit = {
    val dummy = DummyContext.setup("127.0.0.1:10000")
    instance = new JournalIndexSpecContext(dummy)
  }
  
  def shutdown(): Unit = {
    DummyContext.shutdown(instance.dummy)
  }
  
  def context = instance
}
