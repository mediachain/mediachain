package io.mediachain

import _root_.io.mediachain.util.orient.MigrationHelper
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach

/**
  * Created by arkadiy on 2/19/16.
  */

trait Orientable extends ForEach[OrientGraph] {
  def foreach[R: AsResult](f: OrientGraph => R): Result = {

    lazy val graph = MigrationHelper.newInMemoryGraph()
    try AsResult(f(graph))
    finally {
      graph.database().drop()
    }
  }
}
