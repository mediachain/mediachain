package io.mediachain.transactor

import io.mediachain.transactor.Dummies.DummyReference

import scala.util.Try



object TypeSerialization {

  import cats.data.Xor
  import io.mediachain.transactor.Types._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.util.cbor.CborCodec

  object CBORTypeNames {
    val Entity = "entity"
    val Artefact = "artefact"
    val EntityChainReference = "entityChainReference"
    val ArtefactChainReference = "artefactChainReference"
    val EntityChainCell = "entityChainCell"
    val ArtefactChainCell = "artefactChainCell"
    val CanonicalEntry = "insert"
    val ChainEntry = "update"
    val JournalBlock = "journalBlock"
  }

  sealed trait DeserializationError

  case class CborDecodingFailed() extends DeserializationError

  case class UnexpectedCborType(message: String) extends DeserializationError

  case class ReferenceDecodingFailed(message: String) extends DeserializationError

  case class TypeNameNotFound() extends DeserializationError

  case class UnknownObjectType(typeName: String) extends DeserializationError

  case class RequiredFieldNotFound(fieldName: String) extends DeserializationError

  def dataObjectFromCbor(cValue: CValue): Xor[DeserializationError, DataObject] =
    fromCbor(cValue) match {
      case Xor.Left(err) => Xor.left(err)
      case Xor.Right(dataObject: DataObject) => Xor.right(dataObject)
      case Xor.Right(unknownObject) =>
        Xor.left(UnexpectedCborType(
          s"Expected DataObject, but got ${unknownObject.getClass.getTypeName}"
        ))
    }

  def journalEntryFromCbor(cValue: CValue): Xor[DeserializationError, JournalEntry] =
    fromCbor(cValue) match {
      case Xor.Left(err) => Xor.left(err)
      case Xor.Right(journalEntry: JournalEntry) => Xor.right(journalEntry)
      case Xor.Right(unknownObject) =>
        Xor.left(UnexpectedCborType(
          s"Expected JournalEntry, but got ${unknownObject.getClass.getTypeName}"
        ))
    }

  def fromCborBytes(bytes: Array[Byte]): Xor[DeserializationError, CborSerializable] =
    CborCodec.decode(bytes) match {
      case (cValue: CValue) :: _ => fromCbor(cValue)
      case Nil => Xor.left(CborDecodingFailed())
    }

  def fromCbor(cValue: CValue): Xor[DeserializationError, CborSerializable] =
    cValue match {
      case (cMap: CMap) => fromCMap(cMap)
      case _ => Xor.left(UnexpectedCborType(
        s"Expected CBOR map, but received ${cValue.getClass.getName}"
      ))
    }

  def fromCMap(cMap: CMap): Xor[DeserializationError, CborSerializable] = {
    val typeNameOpt: Option[String] =
      cMap.getAs[CString]("type").map(_.string)

    Xor.fromOption(typeNameOpt, TypeNameNotFound())
      .flatMap(name => fromCMap(cMap, name))
  }


  def fromCMap(cMap: CMap, typeName: String)
  : Xor[DeserializationError, CborSerializable] = typeName match {
      case CBORTypeNames.Entity =>
        Xor.right(Entity(meta = cMap.asStringKeyedMap - "type"))

      case CBORTypeNames.Artefact =>
        Xor.right(Artefact(meta = cMap.asStringKeyedMap - "type"))

      case CBORTypeNames.EntityChainCell =>
        entityChainCellFromCMap(cMap)

      case CBORTypeNames.ArtefactChainCell =>
        artefactChainCellFromCMap(cMap)

      case CBORTypeNames.EntityChainReference =>
        entityChainReferenceFromCMap(cMap)

      case CBORTypeNames.ArtefactChainReference =>
        artefactChainReferenceFromCMap(cMap)

      case CBORTypeNames.CanonicalEntry =>
        canonicalEntryFromCMap(cMap)

      case CBORTypeNames.ChainEntry =>
        chainEntryFromCMap(cMap)

      case CBORTypeNames.JournalBlock =>
        journalBlockFromCMap(cMap)

      case _ => Xor.left(UnknownObjectType(typeName))
    }


  def entityChainCellFromCMap(cMap: CMap)
  : Xor[DeserializationError, EntityChainCell] = {
    val entityXor = getRequiredReference(cMap, "entity")
    entityXor.map { entityRef: Reference =>
      EntityChainCell(
        entity = entityRef,
        chain = getOptionalReference(cMap, "chain"),
        meta = cMap.asStringKeyedMap -- Seq("type", "entity", "chain")
      )
    }
  }

  def artefactChainCellFromCMap(cMap: CMap)
  : Xor[DeserializationError, ArtefactChainCell] = {
    val artefactXor = getRequiredReference(cMap, "artefact")
    artefactXor.map { artefactRef: Reference =>
      ArtefactChainCell(
        artefact = artefactRef,
        chain = getOptionalReference(cMap, "chain"),
        meta = cMap.asStringKeyedMap -- Seq("type", "artefact", "chain")
      )
    }
  }


  def entityChainReferenceFromCMap(cMap: CMap)
  : Xor[DeserializationError, EntityChainReference] =
    Xor.right(
      EntityChainReference(getOptionalReference(cMap, "chain"))
    )

  def artefactChainReferenceFromCMap(cMap: CMap)
  : Xor[DeserializationError, ArtefactChainReference] =
    Xor.right(
      ArtefactChainReference(getOptionalReference(cMap, "chain"))
    )

  def journalEntryFromCMap(cMap: CMap)
  : Xor[DeserializationError, JournalEntry] =
    for {
      typeName <- getRequired[CString](cMap, "type").map(_.string)
      entry <- typeName match {
        case CBORTypeNames.CanonicalEntry => canonicalEntryFromCMap(cMap)
        case CBORTypeNames.ChainEntry => chainEntryFromCMap(cMap)
        case _ => Xor.left(UnknownObjectType(typeName))
      }
    } yield entry


  def canonicalEntryFromCMap(cMap: CMap)
  : Xor[DeserializationError, CanonicalEntry] =
    for {
      index <- getRequired[CInt](cMap, "index").map(_.num)
      ref <- getRequiredReference(cMap, "ref")
    } yield CanonicalEntry(index, ref)


  def chainEntryFromCMap(cMap: CMap)
  : Xor[DeserializationError, ChainEntry] =
    for {
      index <- getRequired[CInt](cMap, "index").map(_.num)
      ref <- getRequiredReference(cMap, "ref")
      chain <- getRequiredReference(cMap, "chain")
    } yield ChainEntry(
      index = index,
      ref = ref,
      chain = chain,
      chainPrevious = getOptionalReference(cMap, "chainPrevious")
    )

  def journalEntriesFromCArray(cArray: CArray)
  : Xor[DeserializationError, Array[JournalEntry]] = {
    val entryCMaps = cArray.items
      .flatMap(v => Try(v.asInstanceOf[CMap]).toOption)

    val entryXors: List[Xor[DeserializationError, JournalEntry]] =
      entryCMaps.map(journalEntryFromCMap)

    val initial: Xor[DeserializationError, List[JournalEntry]] =
      Xor.right[DeserializationError, List[JournalEntry]](List())

    val entriesListXor: Xor[DeserializationError, List[JournalEntry]] =
      entryXors.foldLeft(initial) { (accum, x) =>
        (accum, x) match {
          case (Xor.Left(err), _) => Xor.left(err)
          case (_, Xor.Left(err)) => Xor.left(err)
          case (Xor.Right(list), Xor.Right(entry)) => Xor.right(entry :: list)
        }
      }

    entriesListXor.map(_.reverse.toArray)
  }

  def journalBlockFromCMap(cMap: CMap)
  : Xor[DeserializationError, JournalBlock] =
    for {
      index <- getRequired[CInt](cMap, "index").map(_.num)
      entriesCArray <- getRequired[CArray](cMap, "entries")
      entries <- journalEntriesFromCArray(entriesCArray)
    } yield
      JournalBlock(
        index = index,
        chain = getOptionalReference(cMap, "chain"),
        entries = entries
      )

  def getRequired[T <: CValue](cMap: CMap, fieldName: String)
  : Xor[RequiredFieldNotFound, T] = Xor.fromOption(
    cMap.getAs[T](fieldName),
    RequiredFieldNotFound(fieldName)
  )


  def getRequiredReference(cMap: CMap, fieldName: String)
  : Xor[DeserializationError, Reference] =
    getRequired[CMap](cMap, fieldName)
    .flatMap(referenceFromCMap)


  def getOptionalReference(cMap: CMap, fieldName: String): Option[Reference] =
    cMap.getAs[CMap](fieldName)
      .flatMap(referenceFromCMap(_).toOption)



  def referenceFromCMap(cMap: CMap): Xor[DeserializationError, Reference] = {
    val linkOpt: Option[CValue] =
      cMap.asStringKeyedMap.get("@link")

    Xor.fromOption(linkOpt, ReferenceDecodingFailed("@link field not found"))
      .flatMap(decodeLinkValue)
  }


  def decodeLinkValue(linkVal: CValue): Xor[DeserializationError, Reference] =
    linkVal match {
      case CString(s) if s.startsWith("dummy@") => {
        val seqno = s.substring("dummy@".length).toInt
        Xor.right(new DummyReference(seqno))
      }
      case _ =>
        Xor.left(ReferenceDecodingFailed(s"Unknown link value $linkVal"))
    }
}
