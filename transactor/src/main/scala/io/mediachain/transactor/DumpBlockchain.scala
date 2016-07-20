package io.mediachain.transactor

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}
import com.amazonaws.AmazonClientException
import org.slf4j.LoggerFactory
import io.mediachain.protocol.Datastore._
import io.mediachain.copycat.{Client, Transport}
import io.mediachain.datastore.DynamoDatastore
import io.mediachain.util.Properties

object DumpBlockchain {
  val logger = LoggerFactory.getLogger("io.mediachain.transactor.DumpBlockchain")
  
  def main(args: Array[String]) {
    if (args.length < 2) {
      println("Expected arguments: config cluster-ip ...")
      System.exit(1)
    }
    
    val config = args.head
    val cluster = args.toList.tail
    val props = Properties.load(config)
    run(props, cluster)
  }
  
  def run(conf: Properties, cluster: List[String]) {
    val client = Client.build(Transport.SSLConfig.fromProperties(conf))
    val datastore = new DynamoDatastore(DynamoDatastore.Config.fromProperties(conf))
    try {
      val block = Await.result(client.currentBlock, Duration.Inf)
      dumpBlockchain(block, datastore)
    } finally {
      client.close()
      datastore.close()
    }
  }
  
  // the blockchain is dumped head to tail, starting from current block
  def dumpBlockchain(block: JournalBlock, datastore: Datastore) {
    def loop(block: JournalBlock) {
      block.entries.reverse.foreach(dumpEntry(_))
      // can't .foreach, break tails recursion
      block.chain match {
        case Some(ref) =>
          val next = fetchBlock(ref)
          println(s"${stringRef(ref)} journalBlock ${next.index}")
          loop(next)
          
        case None => ()
      }
    }
    
    def dumpEntry(entry: JournalEntry) {
      entry match {
        case e: CanonicalEntry =>
          dumpObject(e.ref, None)
                  
        case e: ChainEntry =>
          dumpObject(e.chain, Some(e.ref))
      }
    }
    
    def dumpObject(oref: Reference, xref: Option[Reference]) {
      val obj = fetchObject(oref)
      val aref = stringRef(oref)
      val bref = xref match {
        case Some(ref) =>
          stringRef(ref)
        case _ => ""
      }
      
      println(s"${aref} ${obj.mediachainType} ${bref}")
    }
    
    def fetchBlock(ref: Reference) = {
      withBackoffRetry(datastore.getAs[JournalBlock](ref))
    }
    
    def fetchObject(ref: Reference) = {
      withBackoffRetry(datastore.get(ref))
    }
    
    def withBackoffRetry[T](op: => Option[T]) = {
      def loop(retry: Int): T = {
        Try(op) match {
          case Success(Some(res)) => res
            
          case Success(None) =>
            val backoff = Client.randomBackoff(retry)
            logger.warn(s"Missing object; retrying in ${backoff} ms")
            loop(retry + 1)

          case Failure(err: AmazonClientException) =>
            logger.error("AWS error", err)
            val backoff = Client.randomBackoff(retry)
            logger.info(s"Retrying in ${backoff} ms")
            Thread.sleep(backoff)
            loop(retry + 1)
          
          case Failure(err: Throwable) =>
            throw err
        }
      }
      
      loop(0)
    }
    
    def stringRef(ref: Reference) = {
      ref match {
        case MultihashReference(mhash) =>
          mhash.base58
        case _ =>
          ref.toString
      }
    }

    loop(block)
  }
}
