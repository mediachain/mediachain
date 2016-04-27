package io.mediachain.transactor

import io.mediachain.transactor.Types._
import org.json4s._

/**
  * Represents a 'multihash' link to an IPFS resource
  *
  * @param multihash - the base58 encoded string representation of an IPFS
  *             multihash
  */
case class IPFSReference(multihash: String) extends Reference

object JsonSerialization {

  /** Like json4s ShortTypeHints, but removes the outer class name for
    * inner classes.  So instead of e.g. `Types$Artefact`, produces
    * `Artefact`
    *
    * These hints are encoded in the `type` field of the serialized json
    * representation.
    */
  private case class ShortTypeHintsIgnoreOuterClass(hints: List[Class[_]])
    extends TypeHints {
    def hintFor(clazz: Class[_]) =
      clazz.getName.substring(clazz.getName.lastIndexOf(".")+1)
        .split('$').toList.last

    def classFor(hint: String) = hints find (hintFor(_) == hint)
  }

  /**
    * List of classes to provide type hints for when serializing.
    */
  private val shortTypeHints = ShortTypeHintsIgnoreOuterClass(List(
    // reference
    classOf[IPFSReference],

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

  /**
    * Rename the `multihash` field to `@link`, to match the IPLD spec
    */
  private val referenceSerializer = FieldSerializer[IPFSReference](
    FieldSerializer.renameTo("multihash", "@link"),
    FieldSerializer.renameFrom("@link", "multihash")
  )

  /**
    * Tells json4s to use the type hints we specified, and to store them
    * in the `type` field.  Also adds the field-renaming serializer for
    * `IPFSReference`
    */
  implicit val jsonFormats = new DefaultFormats {
    override val dateFormat = DefaultFormats.lossless.dateFormat
    override val typeHints = shortTypeHints
    override val typeHintFieldName: String = "type"
  } + referenceSerializer


  // Below are conversion functions from transactor types to
  // json4s `JObject`s.  We may want to write a better conversion
  // interface/

  def toJObject(record: Record): JObject =
    Extraction.decompose(record).asInstanceOf[JObject]

  def toJObject(chainCell: ChainCell): JObject =
    Extraction.decompose(chainCell).asInstanceOf[JObject]

  def toJObject(ref: IPFSReference): JObject =
    Extraction.decompose(ref).asInstanceOf[JObject]

  def toJObject(journalEntry: JournalEntry): JObject =
    Extraction.decompose(journalEntry).asInstanceOf[JObject]
}
