package io.mediachain.transactor

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global
import cats.data.Xor
import io.mediachain.util.cbor.CborAST._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.Transactor._
import io.mediachain.copycat.Client

// A simple client that batch builds a arbtrary long artefact chains
object BatchTestJournalClient {
  def main(args: Array[String]) {
    if (args.length != 4) {
      println("Arguments: server-address magic chains chain-length")
      System.exit(1)
    }
    
    val server = args(0)
    val magic = args(1)
    val chains = args(2).toInt
    val chainlen = args(3).toInt
    
    val client = Client.build()
    println(s"Connecting to $server..." )
    client.connect(server)
    println("Connected")
    run(client, magic, chains, chainlen)
    client.close()
  }
  
  def run(client: Client, magic: String, chains: Int, chainlen: Int) {
      val ops = (1 to chains).map {n => createChain(client, s"${magic}#${n}", chainlen)}
      ops.foreach {op => Await.result(op, Duration.Inf)}
    }
  
  def createChain(client: Client, magic: String, chainlen: Int) =
    TestJournalClient.createChain(client, magic, chainlen)
  
  def fail(what: String): Future[Unit] =
    Future.failed {throw new RuntimeException(what)}
}
