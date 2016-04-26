package io.mediachain.transactor

import io.mediachain.transactor.Types._
import org.json4s._

case class IPLDReference(link: String) extends Reference

object JsonSerialization {

  /** Like json4s ShortTypeHints, but removes the outer class name for
    * inner classes.  So instead of e.g. `Types$Artefact`, produces
    * `Artefact`
    */
  private case class ShortTypeHintsIgnoreOuterClass(hints: List[Class[_]])
    extends TypeHints {
    def hintFor(clazz: Class[_]) =
      clazz.getName.substring(clazz.getName.lastIndexOf(".")+1)
        .split('$').toList.last

    def classFor(hint: String) = hints find (hintFor(_) == hint)
  }

  private val shortTypeHints = ShortTypeHintsIgnoreOuterClass(List(
    // reference
    classOf[IPLDReference],

    // canonical records
    classOf[Entity],
    classOf[Artefact],

    // chain cells
    classOf[EntityChainCell],
    classOf[ArtefactChainCell],

    // journal entries
    classOf[CanonicalEntry],
    classOf[ChainEntry]
  ))

  private val referenceSerializer = FieldSerializer[IPLDReference](
    FieldSerializer.renameTo("link", "@link"),
    FieldSerializer.renameFrom("@link", "link")
  )

  implicit val jsonFormats = new DefaultFormats {
    override val dateFormat = DefaultFormats.lossless.dateFormat
    override val typeHints = shortTypeHints
    override val typeHintFieldName: String = "type"
  } + referenceSerializer



  def toJObject(record: Record): JObject =
    Extraction.decompose(record).asInstanceOf[JObject]

  def toJObject(chainCell: ChainCell): JObject =
    Extraction.decompose(chainCell).asInstanceOf[JObject]

  def toJObject(ref: IPLDReference): JObject =
    Extraction.decompose(ref).asInstanceOf[JObject]

  def toJObject(journalEntry: JournalEntry): JObject =
    Extraction.decompose(journalEntry).asInstanceOf[JObject]
}
