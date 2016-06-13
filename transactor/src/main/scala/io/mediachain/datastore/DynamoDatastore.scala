package io.mediachain.datastore

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.ByteBuffer

import scala.collection.mutable.{ArrayBuffer, Buffer}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.Datastore.DatastoreException


// TODO use batch read/writes for chunked get/puts
class DynamoDatastore(config: DynamoDatastore.Config)
  extends BinaryDatastore with AutoCloseable {
  import scala.collection.JavaConversions._
  
  val table = config.baseTable
  val chunkTable = table + "Chunks"
  val chunkSize = 1024 * 384 // 384 KB; DynamoDB has 400KB limit for item size.
                             // this item size includes field names as well as
                             // values.

  val db = new AmazonDynamoDBClient()
  config.endpoint.foreach(ep => db.setEndpoint(ep))

  override def putData(key: MultiHash, value: Array[Byte]) {
    if (value.length < chunkSize) {
      putSimple(key, value)
    } else {
      putChunked(key, value)
    }
  }

  private def putSimple(key: MultiHash, value: Array[Byte]) {
    val keyAttr = new AttributeValue
    keyAttr.setS(key.base58)
    
    val dataAttr = new AttributeValue
    dataAttr.setB(bytes2Buffer(value))
    
    val item = Map(
      "multihash" -> keyAttr,
      "data"      -> dataAttr
    )
    
    db.putItem(table, item)
  }

  private def putChunked(key: MultiHash, value: Array[Byte]) {
    val key58 = key.base58
    val chunks = chunkCount(value.length)
    val chunkIds = (1 to chunks).map { n => s"${key58}#${n}" }
    
    // this should use the BatchWriteItemRequest API (PITA alert)
    chunkIds.zipWithIndex.foreach {
      case (chunkId, index) => {
        val keyAttr = new AttributeValue
        keyAttr.setS(chunkId)

        val dataAttr = new AttributeValue
        dataAttr.setB(bytes2BufferChunk(value, index))
        
        val item = Map(
          "chunkId" -> keyAttr,
          "data" -> dataAttr
        )
        
        db.putItem(chunkTable, item)
      }
    }
    
    val keyAttr = new AttributeValue
    keyAttr.setS(key58)
    
    val chunkAttr = new AttributeValue
    chunkAttr.setSS(chunkIds)
    
    val item = Map(
      "multihash" -> keyAttr,
      "chunks" -> chunkAttr
      )
    
    db.putItem(table, item)
  }

  private def chunkCount(len: Int): Int = {
    val div = len / chunkSize
    val rem = len % chunkSize
    if (rem > 0) {
      div + 1
    } else {
      div
    }
  }
  
  private def bytes2Buffer(bytes: Array[Byte]) = 
    ByteBuffer.wrap(bytes)
  
  private def bytes2BufferChunk(bytes: Array[Byte], chunk: Int) = {
    val offset = chunk * chunkSize
    val len = Math.min(chunkSize, bytes.length - offset)
    ByteBuffer.wrap(bytes.slice(offset, offset + len))
  }
  
  override def close() {
    db.shutdown()
  }

  override def getData(key: MultiHash): Option[Array[Byte]] = {
    val keyAttr = new AttributeValue
    keyAttr.setS(key.base58)
    
    val item = Map(
      "multihash" -> keyAttr
    )
    val res = db.getItem(table, item).getItem  // yes, really.
    
    Option(res).map { item =>
      val data = item.get("data")
      val chunks = item.get("chunks")
    
      if (data != null) {
        buffer2Bytes(data.getB)
      } else if (chunks != null) {
        getChunks(chunks.getSS.toList)
      } else {
        throw new DatastoreException("Bad record: " + key.base58)
      }
    }
  }
  
  private def getChunks(chunkIds: List[String]) = {
    val buf = new ArrayBuffer[Byte]
    // this should use the BatchGetItemRequest API (PITA alert)
    chunkIds.foreach { chunkId =>
      val keyAttr = new AttributeValue
      keyAttr.setS(chunkId)
      
      val item = Map(
        "chunkId" -> keyAttr
      )
      val res = db.getItem(chunkTable, item).getItem // yes, please.
      if (res != null) {
        val data = res.get("data")
        if (data != null) {
          val bytes = buffer2Bytes(data.getB)
          buf ++= bytes
        } else {
          throw new DatastoreException("Bad chunk record: " + chunkId)
        }
      } else {
        throw new DatastoreException("Missing chunk: " + chunkId)
      }
    }
    buf.toArray
  }
  
  private def buffer2Bytes(buf: ByteBuffer) = 
    buf.array
}

object DynamoDatastore {
  case class Config(
    baseTable: String,
    endpoint: Option[String] = None
  )
}
