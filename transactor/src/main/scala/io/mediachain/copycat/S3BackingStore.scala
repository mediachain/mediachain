package io.mediachain.copycat

import java.util.Properties
import java.util.concurrent.{ExecutorService, Executors, TimeUnit, LinkedBlockingQueue}
import java.io.{File, FileOutputStream}
import java.nio.file.Files
import com.amazonaws.AmazonClientException
import com.amazonaws.auth.{AWSCredentials, BasicAWSCredentials}
import com.amazonaws.services.s3.AmazonS3Client
import org.slf4j.{Logger, LoggerFactory}
import scala.concurrent.{Future, Await}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.collection.JavaConversions._
import scala.util.{Try, Success, Failure}
import cats.data.Xor
import io.mediachain.protocol.Transactor._
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.CborSerialization._
import io.mediachain.copycat.Client.{ClientState, ClientStateListener}
import io.mediachain.datastore.DynamoDatastore
import io.mediachain.multihash.MultiHash

class S3BackingStore(config: S3BackingStore.Config)
extends JournalListener with ClientStateListener with AutoCloseable {
  import S3BackingStore.multihashRef
  private val logger = LoggerFactory.getLogger(classOf[S3BackingStore])
  private val exec = Executors.newScheduledThreadPool(Math.max(4, Runtime.getRuntime.availableProcessors))
  private val datastore = new DynamoDatastore(config.dynamo)
  private val s3 = new AmazonS3Client(config.awscreds)
  private val s3bucket = config.s3bucket
  private val client = Client.build(config.sslConfig)
  private var cluster: Option[List[String]] = None
  private var blocks: Set[Reference] = Set()
  
  client.addStateListener(this)

  def connect(addrs: List[String]) {
    cluster = Some(addrs)
    client.connect(addrs)
    client.listen(this)
  }
  
  def close() {
    exec.shutdownNow()
    client.close()
    datastore.close()
    s3.shutdown()
  }
  
  // Client state listener
  def onStateChange(state: ClientState) {
    state match {
      case ClientState.Connected =>
        logger.info("Client connected; scheduling blockchain catchup")
        exec.submit(new Runnable {
          def run { withErrorLog(writeCurrentBlock()) }
        })
      case ClientState.Suspended => ()
      case ClientState.Disconnected =>
        logger.info("Client disconnected; scheduling reconnect")
        exec.submit(new Runnable {
          def run { withErrorLog(reconnect()) }
        })
    }
  }
  
  // JournalListener
  def onJournalCommit(entry: JournalEntry) {}

  def onJournalBlock(ref: Reference) {
    logger.info(s"New block ${ref}")
    execWriteBlock(ref)
  }
  
  private def withErrorLog(expr: => Unit) {
    try {
      expr
    } catch {
      case e: Throwable =>
        logger.error("Unhandled exception in task", e)
    }
  }
  
  private def writeCurrentBlock() {
    logger.info("Fetching current block")
    val op = client.currentBlock
    val block = Await.result(op, Duration.Inf)
    logger.info("Current blockchain pointer is " + block.chain)
    block.chain.foreach { execWriteBlock(_) }
  }
  
  private def execWriteBlock(ref: Reference) {
    this.synchronized {
      if (!blocks.contains(ref)) {
        blocks += ref
        exec.submit(new Runnable {
          def run { withErrorLog(writeBlock(ref)) }
        })
      }
    }
  }
  
  private def writeBlock(ref: Reference) {
    val mref = multihashRef(ref)
    val ref58 = mref.multihash.base58
    logger.info(s"Fetching block ${ref58}")
    val block = fetchBlock(mref)
    block.chain.foreach { execWriteBlock(_) }
    // check S3 first to see if we have already archived the block
    // (perhaps in a previous run)
    // XXX S3 errors
    val listing = s3.listObjects(s3bucket, ref58)
    if (listing.getObjectSummaries.isEmpty) {
      writeBlockArchive(mref, block)
    } else {
      logger.info(s"Block archive ${ref58} is already stored; skipping")
    }
  }
  
  private def writeBlockArchive(ref: MultihashReference, block: JournalBlock) {
    import sys.process._
    val archive = fetchBlockArchive(ref, block)
    val ref58 = ref.multihash.base58
    val key = ref58 + ":" + block.index + ".gz"
    logger.info(s"Writing block archive ${ref58} -> ${key}")
    val path = "/tmp/" + ref58
    val ostream = new FileOutputStream(path)
    ostream.write(archive.toCborBytes)
    ostream.flush()
    ostream.close()
    s"gzip ${path}".!
    val gzpath = path + ".gz"
    // XXX S3 errors
    s3.putObject(s3bucket, key, new File(gzpath))
    s"rm ${gzpath}".!
  }
  
  private def fetchBlock(ref: MultihashReference) =
    fetchObject[JournalBlock](ref)
  
  private def fetchBlockArchive(ref: MultihashReference, block: JournalBlock) = {
    def addEntry(data: Map[Reference, Array[Byte]], ref: Reference) = {
      val bytes = fetchData(multihashRef(ref))
      var newdata = data + (ref -> bytes)
      val rec = decodeObject[Record](bytes)
      rec.metaSource.foreach { ref =>
        newdata += (ref -> fetchData(multihashRef(ref)))
      }
      newdata
    }
    
    logger.info(s"Fetching block archive ${ref.multihash.base58}")
    var data: Map[Reference, Array[Byte]] = Map()
    block.entries.foreach {
      case entry: CanonicalEntry => 
        data = addEntry(data, entry.ref)
      case entry: ChainEntry =>
        data = addEntry(data, entry.chain)
    }
    
    JournalBlockArchive(ref, block, data)
  }
  
  private def fetchObject[T <: CborSerializable](ref: MultihashReference) =
    decodeObject[T](fetchData(ref))
  
  private def decodeObject[T <: CborSerializable](bytes: Array[Byte]) =
    fromCborBytes[T](bytes) match {
      case Xor.Right(obj) => obj
      case Xor.Left(err) => throw new RuntimeException("Block deserialization error " + err)
    }

  private def fetchData(ref: MultihashReference) = {
    def loop(ref: MultihashReference, retry: Int): Array[Byte] = {
      lazy val ref58 = ref.multihash.base58
      Try(datastore.getData(ref.multihash)) match {
        case Success(Some(bytes)) => bytes
          
        case Success(None) =>
          // hasn't reached the store yet, backoff and retry
          val backoff = Client.randomBackoff(retry)
          logger.info(s"Missing data for ${ref58}; retrying in ${backoff} ms")
          Thread.sleep(backoff)
          loop(ref, retry + 1)
          
        case Failure(err: AmazonClientException) =>
          logger.error(s"AWS error fetching ${ref58}", err)
          val backoff = Client.randomBackoff(retry)
          logger.info(s"Retrying ${ref58} in ${backoff} ms")
          Thread.sleep(backoff)
          loop(ref, retry + 1)
          
        case Failure(err: Throwable) =>
          throw err
      }
    }
    
    loop(ref, 0)
  }
    
  private def reconnect(retry: Int = 0) {
    try {
      cluster.foreach { addrs =>
        logger.info(s"Attempting reconnect to ${addrs}")
        client.connect(addrs)
      }
    } catch {
      case e: Throwable =>
        logger.error("Error connecting to cluster", e)
        val backoff = Client.randomBackoff(retry, 300)
        logger.info(s"Retrying reconnect in ${backoff} ms")
        exec.schedule(new Runnable {
          def run { withErrorLog(reconnect(retry + 1)) }
        }, backoff, TimeUnit.MILLISECONDS)
    }
  }
}

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

object S3BackingStore {
  def multihashRef(ref: Reference): MultihashReference = {
    ref match {
      case xref: MultihashReference => 
        // Leeloo Dallas, mul-ti-hash!
        xref
      case _ => throw new RuntimeException("bogus reference " + ref)
    }
  }
  
  case class Config(
    s3bucket: String,
    awscreds: AWSCredentials,
    dynamo: DynamoDatastore.Config,
    sslConfig: Option[Transport.SSLConfig]
  )

  object Config {
    def fromProperties(conf: Properties) = {
      def getq(key: String): String =
        getopt(key).getOrElse {throw new RuntimeException("Missing configuration property: " + key)}
      
      def getopt(key: String) =
        Option(conf.getProperty(key))
      
      val s3bucket = getq("io.mediachain.transactor.s3.bucket")
      val awsaccess = getq("io.mediachain.transactor.awscreds.access")
      val awssecret = getq("io.mediachain.transactor.awscreds.secret")
      val awscreds = new BasicAWSCredentials(awsaccess, awssecret)
      val dynamoConfig = DynamoDatastore.Config.fromProperties(conf)
      val sslConfig = Transport.SSLConfig.fromProperties(conf)
      Config(s3bucket, awscreds, dynamoConfig, sslConfig)
    }
  }
  
}
