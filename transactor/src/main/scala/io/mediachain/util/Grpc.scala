package io.mediachain.util

import java.net.{InetAddress, InetSocketAddress, SocketAddress}
import scala.collection.mutable.{HashSet => MHashSet}
import io.grpc._
import io.grpc.ServerCall.Listener
import org.slf4j.LoggerFactory

object Grpc {
  def loggingInterceptor(svc: String): ServerInterceptor = {
    new ServerInterceptor {
      val uniqueClientAddresses = new MHashSet[InetAddress]
      val logger = LoggerFactory.getLogger("UniqueClientIP")
      
      override def interceptCall[ReqT, RespT](
        call: ServerCall[ReqT, RespT],
        headers: Metadata,
        next: ServerCallHandler[ReqT, RespT]): Listener[ReqT] = {

        call.attributes().get(ServerCall.REMOTE_ADDR_KEY) match {
          case inet: InetSocketAddress =>
            val address = inet.getAddress
            if (uniqueClientAddresses.add(address)) {
              logger.info(s"${svc} ${address}")
            }
          case nonInet =>
            // should only be hit during in-process transport (unit tests, etc)
            logger.debug(s"${svc}: Connection from non-inet socket: $nonInet")
        }

        next.startCall(call, headers)
      }
    }
  }
}
