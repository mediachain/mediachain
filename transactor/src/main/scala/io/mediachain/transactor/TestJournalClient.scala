package io.mediachain.transactor

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import cats.data.Xor
import io.mediachain.util.cbor.CborAST._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor._
import io.mediachain.copycat.Client

// A simple client that builds an arbitrary long artefact chain
object TestJournalClient {
  def main(args: Array[String]) {
    if (args.length != 3) {
      println("Arguments: server-address magic chain-length")
      System.exit(1)
    }
    
    val server = args(0)
    val magic = args(1)
    val chainlen = args(2).toInt
    
    val client = Client.build()
    println(s"Connecting to $server..." )
    client.connect(server)
    println("Connected")
    run(client, magic, chainlen)
    client.close()
  }
  
  def run(client: Client, magic: String, chainlen: Int) {
    println("insert artefact with magic " + magic)
    val artefact = Artefact(Map("magic" -> CString(magic)))
    val op = client.insert(artefact)
      .flatMap { 
      case Xor.Left(err) =>
        fail(err.toString)
      case Xor.Right(entry) =>
        val ref = entry.ref
        println("Artefact reference: " + ref + "; building chain")
        buildChain(client, ref, chainlen)
    }
    Await.result(op, Duration.Inf)
  }
  
  def buildChain(client: Client, ref: Reference, chainlen: Int) = {
    def loop(n: Int): Future[Unit] = {
      if (n < chainlen) {
        if ((n % 1000) == 0) {
          println(s"${n}...")
        }
        client.update(ref, ArtefactChainCell(ref, None, Map()))
          .flatMap {
          case Xor.Left(err) =>
            fail(err.toString)
          case Xor.Right(_) =>
            loop(n + 1)
        }
      } else {
        println(n)
        Future {}
      }
    }
    loop(0)
  }
  
  def fail(what: String): Future[Unit] =
    Future.failed {throw new RuntimeException(what)}
}
