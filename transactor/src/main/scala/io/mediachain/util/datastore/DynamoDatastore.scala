package io.mediachain.util.datastore

import io.mediachain.hashing.MultiHash
import io.mediachain.transactor.Types._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.ByteBuffer

class DynamoDatastore(table: String, creds: BasicAWSCredentials)
  extends Datastore with AutoCloseable {

  val db = new AmazonDynamoDBClient(creds)

  override def put(obj: Array[Byte], hash: MultiHash): Unit = {
    import scala.collection.JavaConversions._

    val hashBytes = new AttributeValue()
    hashBytes.setB(ByteBuffer.wrap(hash.bytes))
    val objBytes = new AttributeValue()
    objBytes.setB(ByteBuffer.wrap(obj))

    val data = Map(
      "multihash" -> hashBytes,
      "data"      -> objBytes
    )

    db.putItem(table, data)
  }

  override def get(ref: MultiHash): Option[DataObject] = ???

  override def close(): Unit = {
    db.shutdown()
  }
}
