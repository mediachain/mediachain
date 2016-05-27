package io.mediachain.transactor

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import cats.data.Xor
import io.mediachain.copycat.Client
import io.mediachain.protocol.Datastore._
import io.mediachain.multihash.MultiHash

object JournalLookup {
  def main(args: Array[String]) {
    if (args.length != 2) {
      println("Expected arguments: server-address multihash")
      System.exit(1)
    }
    
    val server = args(0)
    val mhash = MultiHash.fromBase58(args(1)) match {
      case Xor.Right(mhash) => mhash
      case Xor.Left(what) =>
        throw new RuntimeException("bad multihash: " + what)
    }
    val mref = MultihashReference(mhash)
    val client = Client.build()
    client.connect(server)
    val res = Await.result(client.lookup(mref), Duration.Inf)
    println(res)
  }
}

