package io.mediachain.util.orient

import com.orientechnologies.orient.core.metadata.schema.{OProperty, OType, OClass}
import org.apache.tinkerpop.gremlin.orientdb.OrientGraph

object OrientSchema {

  sealed trait PropertyBuilder {
    val name: String

    def oType: OType = this match {
      case _: StringProperty => OType.STRING
      case _: IntProperty => OType.INTEGER
      case _: DoubleProperty => OType.DOUBLE
    }

    protected var isMandatory = false
    protected var isReadOnly = false

    def mandatory(m: Boolean): this.type = {
      isMandatory = m
      this
    }

    def readOnly(r: Boolean): this.type = {
      isReadOnly = r
      this
    }

    def addTo(cls: OClass): OProperty = {
      val prop = cls.createProperty(this.name, this.oType)
      prop.setMandatory(isMandatory)
      prop.setReadonly(isReadOnly)
      prop
    }
  }

  case class StringProperty(name: String) extends PropertyBuilder
  case class IntProperty(name: String) extends PropertyBuilder
  case class DoubleProperty(name: String) extends PropertyBuilder

  sealed trait ClassBuilder {
    val name: String
    val props: Seq[PropertyBuilder]
  }

  case class VertexClass(name: String, props: PropertyBuilder*)
    extends ClassBuilder

  case class EdgeClass(name: String, props: PropertyBuilder*)
    extends ClassBuilder

  implicit class OClassHelper(cls: OClass) {
    def add(prop: PropertyBuilder): OProperty = {
      prop.addTo(cls)
    }

    def +(prop: PropertyBuilder): OProperty = add(prop)

    def ++(props: Seq[PropertyBuilder]): Seq[OProperty] =
      props.map(add)
  }


  implicit class OGraphHelper(graph: OrientGraph) {
    private implicit val db = graph.getRawDatabase

    def add(classBuilder: ClassBuilder): OClass = {
      val cls = classBuilder match {
        case _ : VertexClass => ODBScala.createVertexClass(classBuilder.name)
        case _ : EdgeClass => ODBScala.createEdgeClass(classBuilder.name)
      }
      classBuilder.props.foreach(cls.add)
      cls
    }

    def +(classBuilder: ClassBuilder): OClass = add(classBuilder)

    def ++(classBuilders: Seq[ClassBuilder]): Seq[OClass] =
      classBuilders.map(add)
  }

}
