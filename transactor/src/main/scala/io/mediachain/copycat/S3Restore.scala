package io.mediachain.copycat

import java.util.concurrent.{TimeUnit, LinkedBlockingQueue}
import java.io.File
import java.nio.file.Files
import com.amazonaws.AmazonClientException
import com.amazonaws.services.s3.AmazonS3Client
import org.slf4j.{Logger, LoggerFactory}
import cats.data.Xor
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Try, Success, Failure}
import cats.data.Xor
import io.mediachain.protocol.Transactor._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.CborSerialization._
import io.mediachain.datastore.DynamoDatastore
import io.mediachain.multihash.MultiHash

class S3Restore(config: S3BackingStore.Config, writeRawMeta: Boolean = true) extends Runnable {
  import S3BackingStore.multihashRef
  private val logger = LoggerFactory.getLogger(classOf[S3Restore])
  private val datastore = new DynamoDatastore(config.dynamo)
  private val s3 = new AmazonS3Client(config.awscreds)
  private val s3bucket = config.s3bucket
  private val client = Client.build(config.sslConfig)
  // dynamo background writing
  @volatile private var shutdown = false
  private val queue = new LinkedBlockingQueue[(Reference, Array[Byte])]
  private val writers = (1 to Runtime.getRuntime.availableProcessors).map { n =>
    new Thread(this, s"S3Restore@${this.hashCode}#write-${n}")
  }

  // replay the blockchain starting with an S3 archive key
  def restore(addr: String, key: String) {
    restore(List(addr), key)
  }

  def restore(cluster: List[String], key: String) {
    logger.info(s"Restoring ${cluster} from ${key}")
    client.connect(cluster)
    doRestore(key)
    close()
  }
  
  def close() {
    logger.info("Shutting down...")
    shutdown = true
    writers.foreach(_.join())
    client.close()
    datastore.close()
    s3.shutdown()
    logger.info("Done")
  }
  
  private def doRestore(key: String) {
    logger.info(s"Restoring blockchain from ${key}")
    writers.foreach(_.start())
    fetchBlockChain(key).foreach(replayBlock(_))
    logger.info("Waiting for background writes...")
    while (!queue.isEmpty) {Thread.sleep(1000)}
  }
  
  private def fetchBlockChain(key: String) = {
    def loop(arxiv: JournalBlockArchive, res: List[JournalBlockArchive])
    : List[JournalBlockArchive] = {
      arxiv.block.chain match {
        case Some(ref) =>
          val blockIndex = arxiv.block.index - arxiv.block.entries.length
          loop(fetchBlockArchive(blockKey(ref, blockIndex)),
               arxiv :: res)
        case None => 
          arxiv :: res
      }
    }
    loop(fetchBlockArchive(key), Nil)
  }
  
  private def fetchBlockArchive(key: String): JournalBlockArchive = {
    import sys.process._
    logger.info(s"Fetching block archive ${key}")
    val s3obj = s3.getObject(s3bucket, key)
    if (s3obj != null) {
      val path = s"/tmp/${key}"
      val gzpath = path + ".gz"
      Files.copy(s3obj.getObjectContent, new File(gzpath).toPath)
      s"gunzip ${gzpath}".!
      val bytes = Files.readAllBytes(new File(path).toPath)
      fromCborBytes[JournalBlockArchive](bytes) match {
        case Xor.Right(arxiv: JournalBlockArchive) => 
          s"rm ${path}".!
          arxiv
        case Xor.Left(what) =>
          throw new RuntimeException(s"Error deserializing archive ${key}: ${what}")
      }
    } else {
      throw new RuntimeException(s"Unknown archive " + key)
    }
  }
  
  private def blockKey(ref: Reference, index: BigInt) = 
    multihashRef(ref).multihash.base58 + ":" + index + ".gz"
  
  private def replayBlock(arxiv: JournalBlockArchive) {
    val ref58 = multihashRef(arxiv.ref).multihash.base58
    logger.info(s"Replaying block ${ref58}")
    Await.result(replayBlockEntries(arxiv.block.entries.toList, arxiv),
                 Duration.Inf)
  }
  
  private def replayBlockEntries(entries: List[JournalEntry], arxiv: JournalBlockArchive) = {
    def loop(hd: Future[Unit], rest: List[JournalEntry]): Future[Unit] = {
      rest match {
        case next :: rest =>
          loop(hd.flatMap {_ => replayEntry(next, arxiv)}, rest)
        case Nil => hd
      }
    }
    
    loop(Future {}, entries)
  }
  
  private def replayEntry(entry: JournalEntry, arxiv: JournalBlockArchive): Future[Unit] = {
    def checkError[T](res: Xor[JournalError, T]) {
      res match {
        case Xor.Right(_) => ()
        case Xor.Left(what) =>
          logger.error(s"Error replaying ${entry}: ${what}")
      }
    }
    
    def writeMeta(rec: Record) {
      if (writeRawMeta) {
        rec.metaSource.foreach {xref => queue.put((xref, getData(arxiv, xref)))}
      }
    }

    entry match {
      case CanonicalEntry(_, ref) =>
        val rec = getRecord[CanonicalRecord](arxiv, ref)
        writeMeta(rec)
        client.insert(rec).map(checkError(_))
          
      case ChainEntry(_, ref, chain, _) =>
        val cell = getRecord[ChainCell](arxiv, chain)
        writeMeta(cell)
        client.update(ref, cell).map(checkError(_))
    }
  }

  private def getRecord[T <: Record](arxiv: JournalBlockArchive, ref: Reference) = {
    val bytes = getData(arxiv, ref)
    fromCborBytes[T](bytes) match {
      case Xor.Right(obj) => obj
      case Xor.Left(err) => throw new RuntimeException("Record deserialization error " + err)
    }
  }
  
  private def getData(arxiv: JournalBlockArchive, ref: Reference) =
    arxiv.data.get(ref) match {
      case Some(bytes) => bytes
      case None =>
        throw new RuntimeException("Bad Archive; Missing data for " + ref)
    }
  
  // background dynamo writer
  def run {
    try {
      while (!shutdown) {
        val next = queue.poll(1000, TimeUnit.MILLISECONDS)
        Option(next).foreach {
          case (ref, data) => writeData(ref, data)
        }
      }
    } catch {
      case e: Throwable =>
        logger.error("Unhandled exception in writer", e)
    }
  }
  
  private def writeData(ref: Reference, bytes: Array[Byte]) {
    def loop(key: MultiHash, retry: Int) {
      Try(datastore.putData(key, bytes)) match {
        case Success(_) => ()
          
        case Failure(e: AmazonClientException) =>
          logger.error(s"AWS Error writing ${key.base58}", e)
          val backoff = Client.randomBackoff(retry)
          logger.info(s"Backing off for ${backoff} ms")
          Thread.sleep(backoff)
          loop(key, retry + 1)

        case Failure(e: Throwable) =>
          throw e
      }
    }
    loop(multihashRef(ref).multihash, 0)
  }
}
