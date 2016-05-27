package io.mediachain.copycat

import scala.collection.mutable.{Set => MSet, HashSet => MHashSet, 
                                 Map => MMap, HashMap => MHashMap,
                                 Buffer, ArrayBuffer}

import io.atomix.copycat.{Command, Query}
import io.atomix.copycat.server.{Commit, StateMachine => CopycatStateMachine, Snapshottable}
import io.atomix.copycat.server.session.{ServerSession, SessionListener}
import io.atomix.copycat.server.storage.snapshot.{SnapshotReader, SnapshotWriter}
import org.slf4j.{Logger, LoggerFactory}

import cats.data.Xor

object StateMachine {
  import io.mediachain.protocol.Datastore._
  import io.mediachain.protocol.Transactor._

  val JournalBlockSize: Int = 4096 // blocksize for Journal Blockchain
  
  case class JournalInsert(
    record: CanonicalRecord
  ) extends Command[Xor[JournalError, CanonicalEntry]]

  case class JournalUpdate( 
    ref: Reference,
    cell: ChainCell
  ) extends Command[Xor[JournalError, ChainEntry]]
  
  case class JournalLookup(
    ref: Reference
  ) extends Query[Option[Reference]]

  case class JournalCurrentBlock() extends Query[JournalBlock]
  
  sealed abstract class JournalEvent extends Serializable
  case class JournalCommitEvent(entry: JournalEntry) extends JournalEvent
  case class JournalBlockEvent(ref: Reference) extends JournalEvent
  
  class JournalState extends Serializable {
    var seqno: BigInt = 0                                     // next entry index
    var index: MMap[Reference, ChainReference] = new MHashMap // canonical -> chain mapp
    var block: Buffer[JournalEntry] = new ArrayBuffer         // current block entries
    var blockchain: Option[Reference] = None                  // blockchain head
  }

  class JournalStateMachine(
    val datastore: Datastore,
    val blocksize: Int = JournalBlockSize // configurable to facilitate testing
  ) extends CopycatStateMachine with Snapshottable with SessionListener {
    private var state: JournalState = new JournalState
    private val clients: MSet[ServerSession] = new MHashSet // this wanted to be called sessions
    private val logger = LoggerFactory.getLogger(classOf[JournalStateMachine])

    private def commitError(what: String) = Xor.left(JournalCommitError(what))
    
    // Journal Interface
    def insert(commit: Commit[JournalInsert]): Xor[JournalError, CanonicalEntry] = {
      try {
        insertRecord(commit.operation)
      } finally {
        commit.release()
      }
    }
    
    private def insertRecord(cmd: JournalInsert): Xor[JournalError, CanonicalEntry] = {
      val rec = cmd.record
      val ref = datastore.put(rec)
      state.index.get(ref) match {
        case Some(_) => commitError("duplicate insert")
        case None => {
          state.index += (ref -> rec.reference)

          val entry = CanonicalEntry(nextSeqno(), ref)
          publishCommit(entry)
          blockExtend(entry)
          Xor.right(entry)
        }
      }
    }

    def update(commit: Commit[JournalUpdate]): Xor[JournalError, ChainEntry] = {
      try {
        updateChain(commit.operation)
      } finally {
        commit.release()
      }
    }
    
    private def updateChain(cmd: JournalUpdate): Xor[JournalError, ChainEntry] = {
      def commit(ref: Reference, newchain: Reference, oldchain: Option[Reference]) = {
        val entry = ChainEntry(nextSeqno(), ref, newchain, oldchain)
        publishCommit(entry)
        blockExtend(entry)
        Xor.right(entry)
      }
      
      val ref = cmd.ref
      val cell = cmd.cell
      state.index.get(ref) match {
        case None => commitError("invalid reference")
        case Some(cref) => {
          (cref, cell) match {
            case (EntityChainReference(chain), EntityChainCell(entity, xchain, meta)) => {
              if (checkUpdate(ref, chain, entity, xchain)) {
                val newcell = cell.cons(chain)
                val newchain = datastore.put(newcell)
                state.index.put(ref, EntityChainReference(Some(newchain)))
                commit(ref, newchain, chain)
              } else commitError("invalid chain cell")
            }
            case (ArtefactChainReference(chain), ArtefactChainCell(artefact, xchain, meta)) => {
              if (checkUpdate(ref, chain, artefact, xchain)) {
                val newcell = cell.cons(chain)
                val newchain = datastore.put(newcell)
                state.index.put(ref, ArtefactChainReference(Some(newchain)))
                commit(ref, newchain, chain)
              } else commitError("invalid chain cell")
            }
            case _ => commitError("invalid chain")
          }
        }
      }
    }
    
    def lookup(commit: Commit[JournalLookup]): Option[Reference] = {
      try {
        state.index.get(commit.operation.ref).flatMap(_.chain)
      } finally {
        commit.release()
      }
    }

    def currentBlock(commit: Commit[JournalCurrentBlock]) : JournalBlock = {
      try {
        JournalBlock(state.seqno, state.blockchain, state.block.toArray)
      } finally {
        commit.release()
      }
    }

    // block generation
    private def blockExtend(entry: JournalEntry) {
      state.block += entry
      if (state.block.length >= blocksize) {
        val entries = state.block.toArray
        val newblock = JournalBlock(state.seqno, state.blockchain, entries)
        val blockref = datastore.put(newblock)
        state.blockchain = Some(blockref)
        state.block.clear()
        publishBlock(blockref)
        logger.info(s"Generated block ${newblock.index} -> ${blockref}")
      }
    }

    // helpers
    private def nextSeqno() = {
      val next = state.seqno
      state.seqno += 1
      next
    }
    
    private def publishCommit(entry: JournalEntry) {
      val event = JournalCommitEvent(entry)
      clients.foreach(_.publish("journal-commit", event))
    }
    
    private def publishBlock(blockref: Reference) {
      val event = JournalBlockEvent(blockref)
      clients.foreach(_.publish("journal-block", event))
    }
    
    private def checkUpdate(ref: Reference, chain: Option[Reference],
                            xref: Reference, xchain: Option[Reference]) = {
      (xref == ref) && (xchain.isEmpty || (xchain == chain))
    }
        
    // Snapshottable
    override def install(reader: SnapshotReader) {
      state = reader.readObject()
    }
    
    override def snapshot(writer: SnapshotWriter) {
      writer.writeObject(state)
    }
    
    // Session Listener
    override def register(ses: ServerSession) {
      clients += ses
    }
    
    override def unregister(ses: ServerSession) {
      clients -= ses
    }

    override def expire(ses: ServerSession) {
      clients -= ses
    }
    
    override def close(ses: ServerSession) {}
  }
}
