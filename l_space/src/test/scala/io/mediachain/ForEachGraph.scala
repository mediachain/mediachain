package io.mediachain

import gremlin.scala.Graph
import io.mediachain.util.orient.MigrationHelper
import org.specs2.execute.{AsResult, Failure, Result, Success}
import org.specs2.specification.{Around, Context}
import org.specs2.specification.core.Env
import org.specs2.specification.create.{ContextualFragmentFactory, FragmentsFactory}

trait ForEachGraph[T] extends FragmentsFactory { outer =>

  protected def forEachGraph(graph: Graph): T


  protected def foreachContext: Env => Context = (env: Env) => new Around {
    def around[R : AsResult](r: =>R) = AsResult(r)
  }

  protected def withNewGraph[R: AsResult](f: T => R): R = {
    val graph = MigrationHelper.newInMemoryGraph()
    try {
      val t = forEachGraph(graph)
      f(t)
    } finally {
      graph.database.drop()
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
