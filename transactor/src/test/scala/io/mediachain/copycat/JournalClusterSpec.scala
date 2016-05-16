package io.mediachain.transactor

import org.specs2.specification.{AfterAll, BeforeAll}
import java.util.concurrent.{BlockingQueue, LinkedBlockingQueue, TimeUnit}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import cats.data.{Xor, XorT}
import cats.std.all._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor._

object JournalClusterSpec extends io.mediachain.BaseSpec
  with BeforeAll
  with AfterAll
{
  val timeout = Duration(120, TimeUnit.SECONDS)

  def is =
    sequential ^
  s2"""
  JournalStateMachine is consistent in a cluster:
   - the cluster can insert and update the journal $insertAndUpdateJournal
   - all clients see the same chain mappings $checkChainViews
   - all clients see the same current block $checkCurrentBlockViews
   - all clients received the same journal commit events $checkCommitEvents
   - the cluster can generate some blocks $generateBlocks
   - all clients received the same journal block events $checkBlockEvents
  """

  def beforeAll() {
    JournalClusterSpecContext.setup()
  }
  
  def afterAll() {
    JournalClusterSpecContext.shutdown()
  }

  def insertAndUpdateJournal = {
    val context = JournalClusterSpecContext.context
    val ops = context.cluster.dummies.map(insertAndUpdate(_))
    val res = ops.map(xort => Await.result(xort.value, timeout))
    context.refs = res.map {
      case Xor.Left(_) => null
      case Xor.Right(tuple) => tuple
    }

    (res(0) must beRightXor) and
    (res(1) must beRightXor) and
    (res(2) must beRightXor)
  }

  private def insertAndUpdate(dummy: DummyContext)
  : XorT[Future, JournalError, (Reference, Reference)] = {
    for {
      entry1 <- XorT(dummy.client.insert(Entity(Map())))
      entry2 <- XorT(dummy.client.update(entry1.ref, EntityChainCell(entry1.ref, None, Map())))
    } yield {
      (entry1.ref, entry2.chain)
    }
  }
  
  def checkChainViews = {
    val context = JournalClusterSpecContext.context
    val views = context.refs.map { 
      case (ref, chainRef) => 
        context.cluster.dummies.map { dummy =>
          val op = dummy.client.lookup(ref)
          Await.result(op, timeout)
        }
    }
    val expected = context.refs.map {
      case (_, chainRef) => context.cluster.dummies.map {_ => Some(chainRef)}
    }
    views must_== expected
  }
  
  def checkCurrentBlockViews = {
    val context = JournalClusterSpecContext.context
    val blocks = context.cluster.dummies.map { dummy =>
      val op = dummy.client.currentBlock
      Await.result(op, timeout)
    }
    (blocks(0).index must_== 6) and 
    (blocks(0).entries.toList must_== blocks(1).entries.toList) and
    (blocks(0).entries.toList must_== blocks(2).entries.toList)
  }
  
  def checkCommitEvents = {
    val context = JournalClusterSpecContext.context
    val commits = context.commits.map(collectQueue(_))
    (commits(0).length must_== 6) and
    (commits(0) must_== commits(1)) and
    (commits(0) must_== commits(2))
  }
  
  private def collectQueue[T](queue: BlockingQueue[T]) = {
    def loop(tail: List[T]): List[T] = {
      val next = queue.poll(1, TimeUnit.SECONDS)
      if (next != null) {
        loop(next :: tail)
      } else tail.reverse
    }
    loop(List())
  }
  
  def generateBlocks = {
    val context = JournalClusterSpecContext.context
    val ops = context.cluster.dummies.zipWithIndex.map { 
      case (dummy, index) =>
        for { 
          _ <- 3 to JournalClusterSpecContext.BlockSize
          ref = context.refs(index)._1
        } yield dummy.client.update(ref, EntityChainCell(ref, None, Map()))
    }
    ops.foreach { opseq => opseq.foreach(Await.result(_, timeout)) }
    ok
  }
  
  def checkBlockEvents = {
    val context = JournalClusterSpecContext.context
    val blocks = context.blocks.map(collectQueue(_))
    (blocks(0).length must_== 3) and
    (blocks(0) must_== blocks(1)) and
    (blocks(0) must_== blocks(2))
  }
}

class JournalClusterSpecContext(val cluster: DummyClusterContext,
                                val commits: Array[BlockingQueue[JournalEntry]],
                                val blocks: Array[BlockingQueue[Reference]]) {
  var refs: Array[(Reference, Reference)] = null
}

object JournalClusterSpecContext {
  val BlockSize = 20
  var instance: JournalClusterSpecContext = null
  
  def setup(): Unit = {
    val cluster = DummyClusterContext.setup("127.0.0.1:10004", 
                                            "127.0.0.1:10005", 
                                            "127.0.0.1:10006",
                                            BlockSize)
    val queues  = cluster.dummies.map { dummy =>
      val commits: BlockingQueue[JournalEntry] = new LinkedBlockingQueue[JournalEntry]
      val blocks: BlockingQueue[Reference] = new LinkedBlockingQueue[Reference]
      dummy.client.listen(new JournalListener {
        def onJournalCommit(entry: JournalEntry) {
          commits.offer(entry)
        }
        def onJournalBlock(ref: Reference) {
          blocks.offer(ref)
        }
      })
      (commits, blocks)
    }
    val commits = queues.map(_._1)
    val blocks = queues.map(_._2)
    instance = new JournalClusterSpecContext(cluster, commits, blocks)
  }
  
  def shutdown(): Unit = {
    DummyClusterContext.shutdown(instance.cluster)
  }
  
  def context = instance
}
