package io.mediachain.core

import gremlin.scala.Vertex
import io.mediachain.signatures.SigningError

sealed abstract class Error

sealed abstract class TranslationError extends Error
object TranslationError {
  case class ParsingFailed(underlying: Throwable) extends TranslationError
  case class ResourceNotReadable(underlying: Throwable) extends TranslationError
  case class InvalidFormat() extends TranslationError
}

sealed abstract class GraphError extends Error
object GraphError {
  case class TooManyRawBlobsError(vertex: Vertex) extends GraphError
  case class AuthorNotFoundError(vertex: Vertex) extends GraphError

  case class CanonicalNotFound() extends GraphError
  case class RawMetadataNotFound() extends GraphError
  case class PersonNotFound() extends GraphError
  case class VertexNotFound() extends GraphError
  case class BlobNotFound() extends GraphError
  case class SubtreeError() extends GraphError

  case class TransactionFailed(reason: Throwable) extends GraphError
}

sealed abstract class MediachainError extends Error
object MediachainError {
  case class Graph(error: GraphError) extends MediachainError
}
