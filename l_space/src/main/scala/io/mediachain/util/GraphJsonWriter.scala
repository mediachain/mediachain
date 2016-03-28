package io.mediachain.util

import java.io._
import java.util

import com.orientechnologies.orient.core.db.record.OTrackedMap
import gremlin.scala._
import org.apache.tinkerpop.gremlin.structure.Direction
import org.apache.tinkerpop.gremlin.structure.io.IoCore
import org.json4s._
import org.json4s.jackson.{Serialization, JsonMethods => Json}
import org.json4s.jackson.Serialization.{write => JsonWrite}

import scala.collection.JavaConversions._

object GraphJsonWriter {

  case class D3Node(label: String, id: String, properties: Map[String, Any])
  case class D3Link(label: String, id: String, properties: Map[String, Any],
    source: Int, target: Int, sourceId: String, targetId: String)

  case class D3Graph(nodes: List[D3Node], links: List[D3Link])

  class EmbeddedMapSerializer extends
    CustomSerializer[util.LinkedHashMap[_, _]](format => (
    {
      case JObject(fields) => {
        val m = new util.LinkedHashMap[String, String]()
        fields.foreach[Unit] { field =>
          val (key: String, jValue: org.json4s.JsonAST.JValue) = field
          m.put(key, Json.compact(jValue))
        }
        m
      }
    },
    {
      case m: util.LinkedHashMap[_, _] =>
        JObject(
          m.entrySet().map { entry =>
            val key = entry.getKey.toString
            val stringVal = entry.getValue.toString
            JField(key, JString(stringVal))
          }.toList
        )
    }
    ))

  def graphToD3JSONString(graph: Graph): String = {
    val noGremlinScalaProp = { name: String =>
      name != "__gs"
    }

    val nodes: List[D3Node] = graph.V.map { v: Vertex =>
      D3Node(v.label, v.id.toString, v.valueMap.filterKeys(noGremlinScalaProp))
    }.toList

    val edges: List[D3Link] = graph.E.map { e: Edge =>
      val inVertexId = e.inVertex().id.toString
      val outVertexId = e.outVertex().id.toString
      val inVertexIndex = nodes.indexWhere(_.id == inVertexId)
      val outVertexIndex = nodes.indexWhere(_.id == outVertexId)

      D3Link(e.label, e.id.toString, e.valueMap.filterKeys(noGremlinScalaProp),
        inVertexIndex, outVertexIndex, inVertexId, outVertexId)
    }.toList

    val d3Graph = D3Graph(nodes, edges)
    val formats = Serialization.formats(NoTypeHints) + new EmbeddedMapSerializer
    JsonWrite(d3Graph)(formats)
  }


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

    def toD3JsonString: String =
      graphToD3JSONString(graph)

    def printD3JsonString(): Unit = {
      println(toD3JsonString)
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
