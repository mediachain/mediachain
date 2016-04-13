package io.mediachain

import com.orientechnologies.orient.core.sql.OCommandSQL
import gremlin.scala._
import io.mediachain.util.GremlinUtils.withTransaction
import io.mediachain.util.orient.MigrationHelper
import org.apache.tinkerpop.gremlin.orientdb.OrientGraphFactory
import org.specs2.execute.{AsResult, Failure, Result, Success}
import org.specs2.specification.{Around, Context}
import org.specs2.specification.core.Env
import org.specs2.specification.create.{ContextualFragmentFactory, FragmentsFactory}

trait ForEachGraph[T] extends FragmentsFactory { outer =>

  protected lazy val graphFactory: OrientGraphFactory =
    MigrationHelper.newInMemoryGraphFactory()


  protected def forEachGraph(graph: Graph): T


  protected def foreachContext: Env => Context = (env: Env) => new Around {
    def around[R : AsResult](r: =>R) = AsResult(r)
  }

  protected def withNewGraph[R: AsResult](f: T => R): R = {
    val graph = graphFactory.getTx
    try {
      val t = forEachGraph(graph)
      f(t)
    } finally {
      val db = graph.getRawDatabase
      // about the UNSAFE here... normally you'd use
      // DELETE VERTEX instead of plain DELETE, so that you're
      // not left with disconnected edges.  But we want to wipe out
      // everything from all vertex and edge classes, so we're not
      // concerned about that.
      db.command(new OCommandSQL("DELETE FROM V UNSAFE")).execute[Long]()
      db.command(new OCommandSQL("DELETE FROM E UNSAFE")).execute[Long]()
    }
  }

  implicit def foreachFunctionToResult[R : AsResult]: AsResult[T => R] =
    new AsResult[T => R] {
      def asResult(f: =>T => R): Result =
        AsResult(withNewGraph(f))
    }

  override protected def fragmentFactory =
    new ContextualFragmentFactory(super.fragmentFactory, foreachContext)
}
