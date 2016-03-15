package org.mediachain

import gremlin.scala.Vertex
import org.mediachain.io.ParsingError
import org.mediachain.signatures.SigningError

sealed abstract class GraphError

object GraphError {
  case class TooManyRawBlobsError(vertex: Vertex) extends GraphError
  case class AuthorNotFoundError(vertex: Vertex) extends GraphError

  case class CanonicalNotFound() extends GraphError
  case class RawMetadataNotFound() extends GraphError
  case class PersonNotFound() extends GraphError
  case class VertexNotFound() extends GraphError
  case class BlobNotFound() extends GraphError
  case class SubtreeError() extends GraphError
}


sealed abstract class MediachainError

object MediachainError {
  case class Graph(error: GraphError) extends MediachainError
  case class Parsing(error: ParsingError) extends MediachainError
  case class Signature(error: SigningError) extends MediachainError
}
