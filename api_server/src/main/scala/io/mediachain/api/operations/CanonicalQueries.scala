package io.mediachain.api.operations

import java.util.UUID

import gremlin.scala._
import io.mediachain.Traversals
import io.mediachain.Types._
import org.json4s._

object CanonicalQueries {
  val PAGE_SIZE = 20

  def listCanonicals(page: Int)(graph: Graph): List[Canonical] = {
    val first = page * PAGE_SIZE
    val last = first + PAGE_SIZE

    graph.V.hasLabel[Canonical].toCC[Canonical]
      .range(first, last).toList
  }


  def canonicalWithID(canonicalID: UUID)(graph: Graph): Option[Canonical] =
    Traversals.canonicalsWithUUID(graph.V, canonicalID)
      .toCC[Canonical]
      .headOption

  def historyForCanonical(canonicalID: UUID)(graph: Graph): JObject = ???
}
