package io.mediachain.transactor

import scala.collection.mutable.{Set => MSet, HashSet => MHashSet, 
                                 Map => MMap, HashMap => MHashMap}

import io.atomix.copycat.{Command, Query}
import io.atomix.copycat.server.{Commit, StateMachine => CopycatStateMachine, Snapshottable}
import io.atomix.copycat.server.session.{ServerSession, SessionListener}
import io.atomix.copycat.server.storage.snapshot.{SnapshotReader, SnapshotWriter}

import cats.data.Xor

object StateMachine {
  import io.mediachain.transactor.Types._
  
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
  
  case class JournalCommitEvent(entry: JournalEntry)

  class JournalStateMachine(
    val datastore: Datastore
  ) extends CopycatStateMachine with Snapshottable with SessionListener {
    private var seqno: BigInt = 0
    private var index: MMap[Reference, CanonicalReference] = new MHashMap    
    private val clients: MSet[ServerSession] = new MHashSet // this wanted to be called sessions

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
      index.get(ref) match {
        case Some(_) => commitError("duplicate insert")
        case None => {
          index += (ref -> rec.reference)

          val entry = CanonicalEntry(nextSeqno(), ref)
          publishCommit(entry)
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
        Xor.right(entry)
      }
      
      val ref = cmd.ref
      val cell = cmd.cell
      index.get(ref) match {
        case None => commitError("invalid reference")
        case Some(cref) => {
          (cref, cell) match {
            case (EntityReference(chain), EntityChainCell(entity, xchain, meta)) => {
              if (checkUpdate(ref, chain, entity, xchain)) {
                val newcell = EntityChainCell(ref, chain, meta)
                val newchain = datastore.put(newcell)
                index.put(ref, EntityReference(Some(newchain)))
                commit(ref, newchain, chain)
              } else commitError("invalid chain cell")
            }
            case (ArtefactReference(chain), ArtefactChainCell(artefact, xchain, meta)) => {
              if (checkUpdate(ref, chain, artefact, xchain)) {
                val newcell = ArtefactChainCell(ref, chain, meta)
                val newchain = datastore.put(newcell)
                index.put(ref, ArtefactReference(Some(newchain)))
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
        index.get(commit.operation.ref) match {
          case Some(cref) => cref.chain
          case None => None
        }
      } finally {
        commit.release()
      }
    }

    // helpers
    private def nextSeqno() = {
      val next = seqno
      seqno += 1
      next
    }
    

    private def publishCommit(entry: JournalEntry) {
      val event = JournalCommitEvent(entry)
      clients.foreach(_.publish("journal-commit", event))
    }
    
    
    private def checkUpdate(ref: Reference, chain: Option[Reference],
                            xref: Reference, xchain: Option[Reference]) = {
      (xref == ref) && (xchain.isEmpty || (xchain == chain))
    }
        
    // Snapshottable
    override def install(reader: SnapshotReader) {
      seqno = reader.readObject()
      index = reader.readObject()
    }
    
    override def snapshot(writer: SnapshotWriter) {
      writer.writeObject(seqno)
      writer.writeObject(index)
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
