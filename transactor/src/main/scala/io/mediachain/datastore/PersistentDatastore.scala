package io.mediachain.datastore

import java.util.Random
import java.util.concurrent.LinkedBlockingDeque
import com.amazonaws.AmazonClientException
import org.slf4j.{Logger, LoggerFactory}
import io.mediachain.multihash.MultiHash
import io.mediachain.util.Properties

class PersistentDatastore(config: PersistentDatastore.Config)
  extends BinaryDatastore with AutoCloseable with Runnable {
  
  val logger = LoggerFactory.getLogger(classOf[PersistentDatastore])
  val dynamo = new DynamoDatastore(config.dynamo)
  val rocks = new RocksDatastore(config.rocks)
  val random = new Random
  val maxBackoffRetry = 60 // second
  val queue = new LinkedBlockingDeque[MultiHash]
  val writers = (1 to config.threads).map { n =>
    new Thread(this, s"PersistentDataStore@${this.hashCode}#write-${n}")
  }
  
  def start() {
    recover()
    writers.foreach(_.start())
  }
  
  override def putData(key: MultiHash, value: Array[Byte]) {
    logger.debug("Queuing write {}", key.base58)
    rocks.putData(key, value)
    queue.putLast(key)
  }

  override def getData(key: MultiHash): Option[Array[Byte]] = {
    rocks.getData(key).orElse {dynamo.getData(key)}
  }
  
  override def close() {
    writers.foreach(_.interrupt())
    writers.foreach(_.join())
    rocks.close()
    dynamo.close()
  }
  
  // background writer
  override def run() {
    try {
      loop(0)
    } catch {
      case e: InterruptedException => ()
      case e: Throwable => logger.error("FATAL: Unhandled exception in background write thread", e)
    }
  }
  
  private def recover() {
    // Crash recovery: schedule all keys still in rocks db for
    //  write-through to dynamo
    rocks.getKeys.foreach {key => 
      logger.debug("Queing incomplete write {}", key.base58)
      queue.putLast(key)
    }
  }
  
  private def loop(backoff: Int) {
    if (!Thread.interrupted) {
      writeNext() match {
        case Some(key) =>
          rocks.removeData(key)
          loop(0)
          
        case None => 
          val xbackoff = Math.min(maxBackoffRetry, Math.max(1, 2 * backoff))
          val sleep = random.nextInt(1000 * xbackoff)
          logger.info("Backing off for " + sleep + "ms; queue backlog: " + queue.size)
          Thread.sleep(sleep)
          loop(xbackoff)
      }
    }
  }
  
  private def writeNext(): Option[MultiHash] = {
    val key = queue.takeFirst
    logger.debug("Writing {}", key.base58)
    try {
      rocks.getData(key).foreach {data => dynamo.putData(key, data)}
      Some(key)
    } catch {
      case e : AmazonClientException =>
        logger.error("AWS Error writing " + key.base58, e)
        queue.putLast(key)
        None
    }
  }
}

object PersistentDatastore {
  case class Config(
    dynamo: DynamoDatastore.Config,
    rocks: String,
    threads: Int
  )
  
  object Config {
    def fromProperties(conf: Properties) = {
      val dynamoConfig = DynamoDatastore.Config.fromProperties(conf)
      val path = conf.getopt("io.mediachain.transactor.datastore.dir")
        .getOrElse { conf.getq("io.mediachain.transactor.server.rootdir") + "/rocks.db" }
      val threads = conf.getopt("io.mediachain.transactor.datastore.threads") match {
        case Some(str) => str.toInt
        case None => Runtime.getRuntime.availableProcessors
      }
      Config(dynamoConfig, path, threads)
    }
  }
}
