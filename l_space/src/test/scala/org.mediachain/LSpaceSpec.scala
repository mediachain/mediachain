package org.mediachain
import org.specs2._
import org.specs2.execute.{AsResult, Result}
import org.specs2.specification.ForEach
import org.apache.tinkerpop.gremlin.orientdb.{OrientGraph, OrientGraphFactory}
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal
import gremlin.scala._

/**
  * Created by arkadiy on 2/19/16.
  */

trait Orientable extends ForEach[OrientGraph] {
  def foreach[R: AsResult](f: OrientGraph => R): Result = {

    // The line below fixes the NoClassDefFound exception when running
    // `sbt test`, but we should find a better place to put it...
    //
    // The problem is that sbt uses a restricted classloader. But if we
    // make the current thread use the default loader for one of our classes
    // instead, it's all good.
    Thread.currentThread().setContextClassLoader(LSpace.getClass.getClassLoader)

    lazy val graph = new OrientGraphFactory(s"memory:test-${math.random}").getNoTx()
    try AsResult(f(graph))
    finally {
      // no-op
    }
  }
}

object LSpaceSpec extends Specification with Orientable {
  def is = s2"something $e1"

  val Author = "author"
  val Photo = "photo"
  val Person = "person"
  val Name = Key[String]("name")
  val Id = Key[String]("id")

  def e1 = { graph: OrientGraph =>
    val molotovMan = graph + (Photo, Name -> "Molotov Man")
    val susan = graph + (Person, Name -> "Susan Meiselas")
    molotovMan --- (Author) --> molotovMan
    graph.V must beNull
  }
}
