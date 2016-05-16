package io.mediachain.transactor

import cats.data.Xor
import io.mediachain.protocol.Datastore.{CanonicalRecord, ChainCell, ChainEntry, _}
import io.mediachain.protocol.Transactor.{JournalCommitError, JournalError, JournalListener}
import io.mediachain.transactor.Copycat.{ClientState, ClientStateListener}
import io.mediachain.transactor.Dummies.DummyReference

import scala.concurrent.{ExecutionContext, Future, Promise}

class SeedingCopycatClient(serverAddress: String)
  (implicit executionContext: ExecutionContext) {

  val client = Copycat.Client.build()

  def replayEntry(journalEntry: JournalEntry, client: Copycat.Client, datastore: Datastore)
    (implicit executionContext: ExecutionContext)
  : Future[Xor[JournalError, JournalEntry]] = {
    journalEntry match {
      case e: CanonicalEntry => {
        val canonical =
          datastore.getAs[CanonicalRecord](e.ref)

        canonical.map(c => client.insert(c)
            .map { res =>
              println(s"replayed entry ${journalEntry.index}. result: $res")
              res
            }
        )
          .getOrElse(
            Future.successful(
              Xor.left[JournalError, JournalEntry](
                JournalCommitError("Unable to replay journal entry: " +
                  s"can't retrieve canonical for ref ${e.ref}"))))
      }
      case e: ChainEntry => {
        val cell = datastore.getAs[ChainCell](e.chain)

        cell.map(c => client.update(c.ref, c))
          .getOrElse(
            Future.successful(
              Xor.left[JournalError, JournalEntry](
                JournalCommitError("Unable to replay journal entry: " +
                  s"can't retrieve chain cell for ref ${e.ref}"))))
      }
    }
  }


  def seed(blockchain: List[JournalBlock], datastore: Datastore): Future[Xor[JournalError, JournalEntry]] = {

    val journalEntries = blockchain.flatMap(_.entries.toList).sortBy(_.index)

    def empty: Future[Xor[JournalError, JournalEntry]] =
      Future(Xor.right(CanonicalEntry(index = -1, ref = new DummyReference(-1))))

    def serialize(f1: => Future[Xor[JournalError, JournalEntry]], f2: => Future[Xor[JournalError, JournalEntry]]) =
      f1.flatMap {
        case res@Xor.Left(err) => Future.successful(res)
        case _ => f2
      }

    val p = Promise[Xor[JournalError, JournalEntry]]()
    client.addStateListener(new ClientStateListener {
      override def onStateChange(state: ClientState): Unit = {
        println(s"client state changed: $state")
//        val serialOps = journalEntries.foldRight(empty) {
//          (entry, accumF) => serialize(accumF, replayEntry(entry, client, datastore))
//        }
        p.completeWith(replayEntry(journalEntries.head, client, datastore))
      }
    })

    p.future.onComplete { _ =>
      println("shutting down seeding client")
      client.close()
    }

    client.connect(serverAddress)
    p.future
  }


}
