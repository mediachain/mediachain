package io.mediachain.util

import java.io._

import gremlin.scala._
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.io.IoCore
import org.json4s.JObject
import org.json4s.jackson.{JsonMethods => Json}

import scala.collection.JavaConversions._

object GraphJsonWriter {

  implicit class GraphIOImplicits(graph: Graph) {
    lazy val writer = graph.io(IoCore.graphson).writer.create

    def toGraphsonString: String = {
      val out = new ByteArrayOutputStream
      writer.writeGraph(out, graph)
      out.toString("UTF-8")
    }

    def printGraphson(): Unit = {
      writer.writeGraph(System.out, graph)
    }
  }

  implicit class VertexIOImplicits(vertex: Vertex) {
    lazy val writer = vertex.graph.io(IoCore.graphson).writer.create

    def toGraphsonString(edgeDirection: Direction = Direction.BOTH): String = {
      val out = new ByteArrayOutputStream
      writer.writeVertex(out, vertex, edgeDirection)
      out.toString("UTF-8")
    }

    def printGraphson(edgeDirection: Direction = Direction.BOTH): Unit = {
      writer.writeVertex(System.out, vertex, edgeDirection)
    }
  }

  implicit class EdgeIOImplicits(edge: Edge) {
    lazy val writer = edge.graph.io(IoCore.graphson).writer.create

    def toGraphsonString: String = {
      val out = new ByteArrayOutputStream
      writer.writeEdge(out, edge)
      out.toString("UTF-8")
    }

    def printGraphson(): Unit = {
      writer.writeEdge(System.out, edge)
    }
  }

  // TOOD: error handling
  def toGraphsonVertexObjects(graph: Graph): Iterator[JObject] = {
    val writer = graph.io(IoCore.graphson).writer.create
    val verts: Iterator[Vertex] = graph.V.toStream.iterator

    verts.map { v =>
      val out = new ByteArrayOutputStream()
      writer.writeVertex(out, v, Direction.BOTH)
      val in = new ByteArrayInputStream(out.toByteArray)
      Json.parse(in).asInstanceOf[JObject]
    }
  }
}
