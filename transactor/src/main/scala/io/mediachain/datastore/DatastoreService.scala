package io.mediachain.datastore

import com.amazonaws.AmazonClientException
import com.google.protobuf.ByteString 
import io.grpc._
import org.slf4j.LoggerFactory

import scala.concurrent.{ExecutionContext, Future, blocking}
import cats.data.Xor

import io.mediachain.util.{Metrics, Grpc}
import io.mediachain.multihash.MultiHash
import io.mediachain.protocol.datastore.Datastore._
import io.mediachain.protocol.types.Types

class DatastoreService(datastore: DynamoDatastore,
                       metrics: Option[Metrics],
                       maxObjectSize: Int = DatastoreService.defaultMaxObjectSize)
                      (implicit val executionContext: ExecutionContext)
  extends DatastoreServiceGrpc.DatastoreService {
  private val logger = LoggerFactory.getLogger(classOf[DatastoreService])
  
  private def rpcMetrics(rpc: String) {
    metrics.foreach { m =>
      m.counter("datastore_rpc", Map("rpc" -> rpc))
    }
  }
  
  private def rpcErrorMetrics(rpc: String, what: String) {
    metrics.foreach { m =>
      m.counter("datastore_error", Map("rpc" -> rpc, "error" -> what))
    }
  }


  override def put(obj: DataObject): Future[Types.MultihashReference] = {
    rpcMetrics("put")
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
      checkObjectSize(data, "put")
      datastore.putData(key, data)
    } catch {
      case e: AmazonClientException => 
        logger.error("AWS error", e)
        rpcErrorMetrics("put", "UNAVAILABLE")
        throw new StatusRuntimeException(
          Status.UNAVAILABLE.withDescription("Resource Temporarily Unavailable; AWS error")
        )
    }
  }
  
  private def checkObjectSize(bytes: Array[Byte], rpc: String) {
    if (bytes.length > maxObjectSize) {
      rpcErrorMetrics(rpc, "INVALID_ARGUMENT")
      throw new StatusRuntimeException(
        Status.INVALID_ARGUMENT.withDescription("Maximum object size exceeded"))
    }
  }

  
  override def get(ref: Types.MultihashReference): Future[DataObject] = {
    rpcMetrics("get")
    val key = MultiHash.fromBase58(ref.reference) match {
      case Xor.Right(mhash) => mhash
      case Xor.Left(err) =>
        rpcErrorMetrics("get", "INVALID_ARGUMENT")
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
          rpcErrorMetrics("get", "NOT_FOUND")
          throw new StatusRuntimeException(
            Status.NOT_FOUND.withDescription("No data for " + key.base58)
          )
      }
    } catch {
      case e: AmazonClientException =>
        logger.error("AWS error", e)
        rpcErrorMetrics("get", "UNAVAILABLE")
        throw new StatusRuntimeException(
          Status.UNAVAILABLE.withDescription("Resource Temporarily Unavailable; AWS error")
        )
    }
  }
}

object DatastoreService {
  val defaultMaxObjectSize = 65536
  def createServer(service: DatastoreService, port: Int)
                  (implicit executionContext: ExecutionContext)
  = {
    import scala.language.existentials
    
    val builder = ServerBuilder.forPort(port)
    val server = builder.addService(
      ServerInterceptors.intercept(
        DatastoreServiceGrpc.bindService(service, executionContext),
        Grpc.loggingInterceptor("DatastoreService")
      )
    ).build
    server
  }
}
