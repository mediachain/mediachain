package io.mediachain.datastore

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.{AttributeValue, KeysAndAttributes}
import java.nio.{ByteBuffer,BufferUnderflowException}
import java.util.{Map => JMap}
import scala.collection.mutable.{Buffer, ArrayBuffer}
import io.mediachain.multihash.MultiHash

// TODO error handling
//      queued eventual writes in the background with disk backing of 
//       in-progress writes
//      use batch writes for chunked puts
class DynamoDatastore(table: String, creds: BasicAWSCredentials)
  extends BinaryDatastore with AutoCloseable {
  import scala.collection.JavaConversions._
  
  val chunkSize = 256 * 384 // 384 KB; DynamoDB has 400KB limit
  val chunkTable = table + "Chunks"
  
  val db = new AmazonDynamoDBClient(creds)

  override def put(key: MultiHash, value: Array[Byte]) {
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
    val chunkIds = (1 to chunks).map { n => key58 + "#" + n }
    
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
  
  private def bytes2Buffer(bytes: Array[Byte]) = {
    val buf = ByteBuffer.allocate(bytes.length + 4)
    buf.putInt(bytes.length)
    buf.put(bytes)
    buf
  }
  
  private def bytes2BufferChunk(bytes: Array[Byte], chunk: Int) = {
    val offset = chunk * chunkSize
    val len = Math.min(chunkSize, bytes.length - offset)
    val buf = ByteBuffer.allocate(len + 4)
    buf.putInt(len)
    buf.put(bytes, offset, len)
    buf
  }
  
  override def close() {
    db.shutdown()
  }

  override def get(key: MultiHash): Option[Array[Byte]] = {
    val keyAttr = new AttributeValue
    keyAttr.setS(key.base58)
    
    val item = Map(
      "multihash" -> keyAttr
    )
    val res = db.getItem(table, item).getItem  // yes, really.
    
    val data = res.get("data")
    val chunks = res.get("chunks")
    
    if (data != null) {
      Some(buffer2Bytes(data.getB))
    } else if (chunks != null) {
      Some(getChunks(chunks.getSS.toList))
    } else {
      None
    }
  }
  
  private def getChunks(chunkIds: List[String]) = {
    val buf = new ArrayBuffer[Byte]
    val chunks = getChunksBatch(chunkIds)
    chunkIds.foreach { chunkId =>
      chunks.get(chunkId) match {
        case Some(attr) => {
          val bytes = buffer2Bytes(attr.getB)
          buf ++= bytes
        }
        case None => throw new RuntimeException("Missing chunk: " + chunkId)
      }
    }
    buf.toArray
  }

  private def getChunksBatch(chunkIds: List[String]) = {
    def loop(req: JMap[String, KeysAndAttributes], chunks: Map[String, AttributeValue])
    : Map[String, AttributeValue] = {
      val res = db.batchGetItem(req)
      val xchunks = res.getResponses.get(chunkTable)
       .map { jmap => (jmap.get("chunkId").getS -> jmap.get("data")) }
      val rest = res.getUnprocessedKeys
      if (rest.isEmpty) {
        chunks ++ xchunks
      } else {
        loop(rest, chunks ++ xchunks)
      }
    }
    
    loop(batchKeysAndAttributes(chunkIds), Map())
  }

  private def batchKeysAndAttributes(chunkIds: List[String]) = {
    val keysAndAttrs = new KeysAndAttributes
    val keys = chunkIds.map { chunkId =>
      val keyAttr = new AttributeValue
      keyAttr.setS(chunkId)
      (Map("chunkId" -> keyAttr) : JMap[String, AttributeValue] )
    }
    keysAndAttrs.setKeys(keys)
    (Map(chunkTable -> keysAndAttrs) : JMap[String, KeysAndAttributes])
  }
  
  private def buffer2Bytes(buf: ByteBuffer) = {
    val len = buf.getInt()
    val data = new Array[Byte](len)
    buf.get(data)
    data
  }
}
