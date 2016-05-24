package io.mediachain.transactor

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import io.mediachain.copycat.Client
import io.mediachain.protocol.Datastore.JournalBlock

object CurrentBlock {
  def main(args: Array[String]) {
    if (args.length != 1) {
      println("Expected arguments: server-address")
      System.exit(1)
    }

    val server = args(0)
    val client = Client.build()
    client.connect(server)
    val block = Await.result(client.currentBlock, Duration.Inf)
    println(s"Current block is ${block.index}; chain pointer is ${block.chain}")
    client.close()
  }
}
