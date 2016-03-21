package io.mediachain.util.orient

import java.util.Date

import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.{OProperty, OType, OClass}
import springnz.orientdb.ODBScala


trait OrientSchema {

  sealed trait PropertyBuilder {
    val name: String

    def oType: OType = this match {
      case _: StringProperty => OType.STRING
      case _: IntProperty => OType.INTEGER
      case _: DoubleProperty => OType.DOUBLE
      case _: DateProperty => OType.DATETIME
      case _: BoolProperty => OType.BOOLEAN
    }

    protected var createUniqueIndex = false
    protected var isMandatory = false
    protected var isReadOnly = false
    protected var defaultValueAsString: Option[String] = None

    def mandatory(m: Boolean): this.type = {
      isMandatory = m
      this
    }

    def readOnly(r: Boolean): this.type = {
      isReadOnly = r
      this
    }

    def unique(u: Boolean): this.type = {
      createUniqueIndex = u
      this
    }

    def addTo(cls: OClass): OProperty = {
      val prop = cls.createProperty(this.name, this.oType)
      prop.setMandatory(isMandatory)
      prop.setReadonly(isReadOnly)
      defaultValueAsString.foreach(v => prop.setDefaultValue(v))
      if (createUniqueIndex) {
        prop.createIndex(OClass.INDEX_TYPE.UNIQUE)
      }

      prop
    }
  }

  sealed trait TypedPropertyBuilder[T] extends PropertyBuilder {
    def defaultValue(value: T): this.type = {
      this.defaultValueAsString = Some(value.toString)
      this
    }
  }

  sealed trait RangedPropertyBuilder[T] extends TypedPropertyBuilder[T] {
    protected var minValue: Option[T] = None
    protected var maxValue: Option[T] = None
    def min(minimumValue: T): this.type = {
      this.minValue = Some(minimumValue)
      this
    }

    def max(maximumValue: T): this.type = {
      maxValue = Some(maximumValue)
      this
    }

    override def addTo(cls: OClass): OProperty = {
      val prop = super.addTo(cls)
      minValue.foreach(v => prop.setMin(v.toString))
      maxValue.foreach(v => prop.setMax(v.toString))
      prop
    }
  }

  case class StringProperty(name: String) extends TypedPropertyBuilder[String]
  case class IntProperty(name: String) extends RangedPropertyBuilder[Int]
  case class DoubleProperty(name: String) extends RangedPropertyBuilder[Double]
  case class DateProperty(name: String) extends TypedPropertyBuilder[Date]
  case class BoolProperty(name: String) extends TypedPropertyBuilder[Boolean]

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


  implicit class DBSchemaHelper(db: ODatabaseDocumentTx) {

    def add(classBuilder: ClassBuilder): OClass = {
      val cls = classBuilder match {
        case _ : VertexClass => ODBScala.createVertexClass(classBuilder.name)(db)
        case _ : EdgeClass => ODBScala.createEdgeClass(classBuilder.name)(db)
      }
      classBuilder.props.foreach(cls.add)
      cls
    }

    def +(classBuilder: ClassBuilder): OClass = add(classBuilder)

    def ++(classBuilders: Seq[ClassBuilder]): Seq[OClass] =
      classBuilders.map(add)
  }

}
