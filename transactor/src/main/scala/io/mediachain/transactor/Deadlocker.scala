package io.mediachain.transactor

import java.util.concurrent.Executors

import io.mediachain.copycat._
import io.mediachain.protocol.Datastore.{Artefact, JournalBlock}
import io.mediachain.util.cbor.CborAST.CInt
import org.slf4j.LoggerFactory

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.Random

object Deadlocker {
  val logger = LoggerFactory.getLogger(Deadlocker.getClass)

  def main(args: Array[String]) {
    if (args.length != 1) {
      logger.info("Arguments: server-address")
      System.exit(1)
    }

    val serverAddress = args(0)
    run(serverAddress)
  }

  val executor = Executors.newFixedThreadPool(4)
  val insertTimeout = 60.seconds
  val currentBlockTimeout = 60.seconds

  def run(serverAddress: String): Unit = {

    // one thread constantly creates new records

    /* -- disabled to test without

    executor.execute(new Runnable {
      override def run(): Unit = {
        val client = Client.build()
        logger.info(s"record creation client connecting to $serverAddress")
        client.connect(serverAddress)
        logger.info("connected")
        while (true) {
          val magicKey = s"foo#${Random.nextInt}"
          val magicVal = Random.nextInt
          val a = Artefact(Map(magicKey -> CInt(magicVal)))
          val result = Await.result(client.insert(a), insertTimeout)
          if (result.isLeft) {
            logger.info(s"Error inserting record: $result")
          }
        }
      }
    })
    */

    // then we make an infinite flatMap chain of requests for the current
    val client = Client.build()
    logger.info(s"current block requester client connecting to $serverAddress")
    client.connect(serverAddress)
    logger.info("connected")

    logger.info("starting to request blocks")
    Await.result(requestCurrentBlock(client), Duration.Inf)
  }

  def requestCurrentBlock(client: Client): Future[JournalBlock] = {
    client.currentBlock.flatMap { b =>
      logger.info(s"got current block with index ${b.index}, requesting again")
      requestCurrentBlock(client)
    }
  }
}
