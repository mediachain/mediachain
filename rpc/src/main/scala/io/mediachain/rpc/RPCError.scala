package io.mediachain.rpc

import io.grpc.{Status, StatusRuntimeException}

sealed trait RPCError {
  val statusCode: Status.Code
  val description: String

  def asStatus: Status =
    Status.fromCodeValue(statusCode.value)
      .withDescription(description)

  def asException: StatusRuntimeException = {
    new StatusRuntimeException(asStatus)
  }
}

object RPCError {
  def fromException(e: StatusRuntimeException): RPCError = {
    val status = e.getStatus

    status.getCode match {
      case c if c == Status.NOT_FOUND.getCode =>
        NotFound(status.getDescription)

      case c if c == Status.FAILED_PRECONDITION.getCode =>
        FailedPrecondition(status.getDescription)

      case _ =>
        GRPCError(status)
    }
  }

  // Catch-all for error types not specifically handled below
  case class GRPCError(status: Status) extends RPCError {
    val statusCode = status.getCode
    val description = status.getDescription

    override def asStatus = status
  }

  // Resource not found (404)
  case class NotFound(description: String) extends RPCError {
    val statusCode = Status.NOT_FOUND.getCode
  }

  case class FailedPrecondition(description: String) extends RPCError {
    val statusCode = Status.FAILED_PRECONDITION.getCode
  }

}
