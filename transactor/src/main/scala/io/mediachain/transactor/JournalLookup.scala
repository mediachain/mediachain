package io.mediachain.transactor

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import cats.data.Xor
import io.mediachain.copycat.{Client, Transport}
import io.mediachain.protocol.Datastore._
import io.mediachain.multihash.MultiHash
import io.mediachain.util.Properties

object JournalLookup {
  def parseArgs(args: Array[String]): (String, MultiHash, Option[Transport.SSLConfig]) = {
    args match {
      case Array(server, mhash58) =>
        (server, parseMultiHash(mhash58), None)
      case Array(server, mhash58, config) =>
        (server, parseMultiHash(mhash58), parseConfig(config))
      case _ =>
        throw new RuntimeException("Expected arguments: server-address multihash [client-config]")
    }
  }
  
  def parseMultiHash(mhash58: String) =
    MultiHash.fromBase58(mhash58) match {
      case Xor.Right(mhash) => mhash
      case Xor.Left(what) =>
        throw new RuntimeException("bad multihash: " + what)
    }
  
  def parseConfig(config: String) = {
    val props = Properties.load(config)
    Transport.SSLConfig.fromProperties(props)
  }

  def main(args: Array[String]) {
    val (server, mhash, sslConfig) = parseArgs(args)
    val mref = MultihashReference(mhash)
    val client = Client.build(sslConfig)
    client.connect(server)
    val res = Await.result(client.lookup(mref), Duration.Inf)
    println(res)
    client.close()
  }
}

