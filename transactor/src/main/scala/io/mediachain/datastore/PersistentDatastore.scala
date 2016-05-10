package io.mediachain.datastore

import java.util.Random
import java.util.concurrent.LinkedBlockingDeque
import com.amazonaws.AmazonServiceException
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.Datastore.DatastoreException

class PersistentDatastore(config: PersistentDatastore.Config)
  extends BinaryDatastore with AutoCloseable with Runnable {
  
  val dynamo = new DynamoDatastore(config.dynamo)
  val rocks = new RocksDatastore(config.rocks)
  val queue = new LinkedBlockingDeque[MultiHash]
  val writer = new Thread(this)
  val random = new Random
  val maxBackoffRetry = 60 // seconds

  override def putData(key: MultiHash, value: Array[Byte]) {
    rocks.synchronized {rocks.putData(key, value)}
    queue.putLast(key)
  }

  override def getData(key: MultiHash): Option[Array[Byte]] = {
    rocks.synchronized {rocks.getData(key)}
      .orElse {dynamo.synchronized {dynamo.getData(key)}}
  }
  
  override def close() {
    writer.interrupt()
    writer.join()
    rocks.close()
    dynamo.close()
  }
  
  // background writer
  override def run() {
    recover()
    loop(0)
  }
  
  private def recover() {
    // XXX Implement me: crash recovery
    //     enqueue all rocks persisted keys for write through to dynamo
  }
  
  private def loop(backoff: Int) {
    if (!Thread.interrupted) {
      writeNext() match {
        case Some(key) => {
          rocks.synchronized {rocks.removeData(key)}
          loop(0)
        }
          
        case None => {
          val xbackoff = Math.min(maxBackoffRetry, Math.max(1, 2 * backoff))
          val sleep = random.nextInt(1000 * xbackoff)
          // XXX log sleep?
          Thread.sleep(sleep)
          loop(xbackoff)
        }
      }
    }
  }
  
  private def writeNext(): Option[MultiHash] = {
    val key = queue.takeFirst
    try {
      rocks.synchronized {rocks.getData(key)}.map { data =>
        dynamo.synchronized {dynamo.putData(key, data)}
        key
      }
    } catch {
      case e: AmazonServiceException => {
        // XXX log exception
        queue.addFirst(key)
        None
      }
    }
  }
}

object PersistentDatastore {
  case class Config(dynamo: DynamoDatastore.Config, rocks: String)
}
