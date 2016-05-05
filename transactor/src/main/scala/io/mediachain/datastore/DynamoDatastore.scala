package io.mediachain.datastore

import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.{ByteBuffer,BufferUnderflowException}
import scala.collection.mutable.{Buffer, ArrayBuffer}
import io.mediachain.multihash.MultiHash

class DynamoDatastore(table: String, creds: BasicAWSCredentials)
  extends BinaryDatastore with AutoCloseable {
  import scala.collection.JavaConversions._

  val db = new AmazonDynamoDBClient(creds)

  override def put(key: MultiHash, value: Array[Byte]) {
    val hashBytes = new AttributeValue()
    hashBytes.setS(key.base58)
    val objBytes = new AttributeValue()
    objBytes.setB(ByteBuffer.wrap(value))

    val data = Map(
      "multihash" -> hashBytes,
      "data"      -> objBytes
    )

    db.putItem(table, data)
  }

  override def close() {
    db.shutdown()
  }

  override def get(key: MultiHash): Option[Array[Byte]] = {
    val hashBytes = new AttributeValue()
    hashBytes.setS(key.base58)
    val data = Map(
      "multihash" -> hashBytes
    )
    val result = db.getItem(table, data)
    val rdata = result.getItem.get("data")
    
    if (rdata != null) {
      Some(bufferBytes(rdata.getB))
    } else {
      None
    }
  }
  
  // this is ugly, perhaps we should serialize len+payload
  private def bufferBytes(ibuf: ByteBuffer): Array[Byte] = {
    def getByte(): Option[Byte] = {
      try {
        Some(ibuf.get())
      } catch {
        case (e: BufferUnderflowException) => None
      }
    }

    def getBytes(obuf: Buffer[Byte]): Array[Byte] = {
      getByte() match {
        case Some(byte) => {
          obuf += byte
          getBytes(obuf)
        }
        case None => obuf.toArray
      }
    }
    
    getBytes(new ArrayBuffer)
  }
}
