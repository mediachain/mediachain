package io.mediachain.util.datastore

import io.mediachain.multihash.MultiHash
import io.mediachain.transactor.Types._
import com.amazonaws.auth.BasicAWSCredentials
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import com.amazonaws.services.dynamodbv2.model.AttributeValue
import java.nio.ByteBuffer

class DynamoDatastore(table: String, creds: BasicAWSCredentials)
  extends MultiHashDatastore with AutoCloseable {
  import scala.collection.JavaConversions._

  val db = new AmazonDynamoDBClient(creds)

  override def put(obj: DataObject): Ref = {
    val bytes = obj.toCborBytes
    val hash = MultiHash.hashWithSHA256(bytes)

    val hashBytes = new AttributeValue()
    hashBytes.setB(ByteBuffer.wrap(hash.bytes))
    val objBytes = new AttributeValue()
    objBytes.setB(ByteBuffer.wrap(bytes))

    val data = Map(
      "multihash" -> hashBytes,
      "data"      -> objBytes
    )

    db.putItem(table, data)

    MultihashReference(hash)
  }

  override def close(): Unit = {
    db.shutdown()
  }

  override def get(ref: Ref): Option[DataObject] = {
    val hashBytes = new AttributeValue()
    hashBytes.setB(ByteBuffer.wrap(ref.multihash.bytes))
    val data = Map(
      "multihash" -> hashBytes
    )
    val result = db.getItem(table, data)
    val bytes = result.getItem.get("data").getB

    // here we need to deserialize the obj pending yusef's chgs
    ???
  }
}
