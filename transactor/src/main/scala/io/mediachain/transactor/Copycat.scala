package io.mediachain.transactor

import io.atomix.copycat.{Command, Query}
import io.atomix.copycat.server.{Commit, StateMachine, Snapshottable}
import io.atomix.copycat.server.storage.snapshot.{SnapshotReader, SnapshotWriter}

import cats.data.Xor

object Copycat {
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

  class JournalStateMachine extends StateMachine with Snapshottable {
    
    override def install(reader: SnapshotReader) {
      
    }
    
    override def snapshot(writer: SnapshotWriter) {

    }
    
    def insert(commit: Commit[JournalInsert]): Xor[JournalError, CanonicalEntry] = {
      Xor.left(JournalError("Implement me!"))
    }
    
    def update(commit: Commit[JournalUpdate]): Xor[JournalError, ChainEntry] = {
      Xor.left(JournalError("Implement me!"))
    }
    
    def lookup(commit: Commit[JournalLookup]): Option[Reference] = {
      None
    }
                                                    
  }
}
