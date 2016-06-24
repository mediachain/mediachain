package io.mediachain.datastore

import com.amazonaws.AmazonClientException
import com.google.protobuf.ByteString 
import io.grpc.{ServerBuilder, Status, StatusRuntimeException}
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future, blocking}
import cats.data.Xor

import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.datastore.Datastore._
import io.mediachain.protocol.types.Types

class DatastoreService(datastore: DynamoDatastore)
                      (implicit val executionContext: ExecutionContext)
  extends DatastoreServiceGrpc.DatastoreService {
  private val logger = LoggerFactory.getLogger(classOf[DatastoreService])
  
  override def put(obj: DataObject): Future[Types.MultihashReference] = {
    val data = obj.data.toByteArray
    val key = MultiHash.hashWithSHA256(data)
    Future {
      blocking {
        putData(key, data)
      }
      Types.MultihashReference(key.base58)
    }
  }
  
  private def putData(key: MultiHash, data: Array[Byte]) {
    try {
      logger.info(s"putData ${key.base58} ${data.length}")
      datastore.putData(key, data)
    } catch {
      case e: AmazonClientException => 
        logger.error("AWS error", e)
        throw new StatusRuntimeException(
          Status.UNAVAILABLE.withDescription("Resource Temporarily Unavailable; AWS error")
        )
    }
  }
  
  override def get(ref: Types.MultihashReference): Future[DataObject] = {
    val key = MultiHash.fromBase58(ref.reference) match {
      case Xor.Right(mhash) => mhash
      case Xor.Left(err) =>
        throw new StatusRuntimeException(
          Status.INVALID_ARGUMENT.withDescription(
            "Invalid multihash reference"
          ))
    }
    
    Future {
      blocking {
        val bytes = getData(key)
        DataObject(ByteString.copyFrom(bytes))
      }
    }
  }
  
  private def getData(key: MultiHash): Array[Byte] = {
    try {
      logger.info(s"getData ${key.base58}")
      datastore.getData(key) match {
        case Some(data) => data
        case None =>
          throw new StatusRuntimeException(
            Status.NOT_FOUND.withDescription("No data for " + key.base58)
          )
      }
    } catch {
      case e: AmazonClientException =>
        logger.error("AWS error", e)
        throw new StatusRuntimeException(
          Status.UNAVAILABLE.withDescription("Resource Temporarily Unavailable; AWS error")
        )
    }
  }
}

object DatastoreService {
  def createServer(service: DatastoreService, port: Int)
                  (implicit executionContext: ExecutionContext)
  = {
    import scala.language.existentials
    
    val builder = ServerBuilder.forPort(port)
    val server = builder.addService(
      DatastoreServiceGrpc.bindService(service, executionContext)
    ).build
    server
  }
}
