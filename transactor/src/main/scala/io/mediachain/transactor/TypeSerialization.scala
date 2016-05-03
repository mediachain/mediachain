package io.mediachain.transactor

import io.mediachain.transactor.Dummies.{DummyReference, DummyReferenceDeserializer}

import scala.util.Try



object TypeSerialization {

  import cats.data.Xor
  import io.mediachain.transactor.Types._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.util.cbor.CborCodec

  object CBORTypeNames {
    val Entity = "entity"
    val Artefact = "artefact"
    val EntityChainCell = "entityChainCell"
    val ArtefactChainCell = "artefactChainCell"
    val CanonicalEntry = "insert"
    val ChainEntry = "update"
    val JournalBlock = "journalBlock"

    // TODO: Include subtypes
    val ArtefactChainCellTypes: Set[String] = Set(
      ArtefactChainCell
    )

    val EntityChainCellTypes: Set[String] = Set(
      EntityChainCell
    )
  }


  trait CborDeserializer[T] {
    def fromCMap(cMap: CMap): Xor[DeserializationError, T]

    def fromCValue(cValue: CValue): Xor[DeserializationError, T] =
      cValue match {
        case (cMap: CMap) => fromCMap(cMap)
        case _ => Xor.left(UnexpectedCborType(
          s"Expected CBOR map, but received ${cValue.getClass.getName}"
        ))
      }
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
      case (_: CTag) :: (taggedValue: CValue) :: _ => fromCbor(taggedValue)
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
  : Xor[DeserializationError, CborSerializable] = {

    // TODO: create multiple deserializer maps for different contexts
    // e.g. DataStore should deserialize chain cells to specific subtypes, etc
    val transactorDeserializers
    : Map[String, CborDeserializer[CborSerializable]] = Map(
      CBORTypeNames.Entity -> EntityDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]],

      CBORTypeNames.Artefact -> ArtefactDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]],

      CBORTypeNames.EntityChainCell -> EntityChainCellDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]],

      CBORTypeNames.ArtefactChainCell -> ArtefactChainCellDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]],

      CBORTypeNames.CanonicalEntry -> CanonicalEntryDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]],

      CBORTypeNames.ChainEntry -> ChainEntryDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]],

      CBORTypeNames.JournalBlock -> JournalBlockDeserializer
        .asInstanceOf[CborDeserializer[CborSerializable]]
    )

    for {
      deserializer <- Xor.fromOption(
        transactorDeserializers.get(typeName),
        UnknownObjectType(typeName)
      )
      value <- deserializer.fromCMap(cMap)
    } yield value
  }


  object EntityDeserializer extends CborDeserializer[Entity]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Entity] =
      assertRequiredTypeName(cMap, CBORTypeNames.Entity).map { _ =>
        Entity(cMap.asStringKeyedMap)
      }
  }

  object ArtefactDeserializer extends CborDeserializer[Artefact]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Artefact] =
      assertRequiredTypeName(cMap, CBORTypeNames.Artefact).map { _ =>
        Artefact(cMap.asStringKeyedMap)
      }
  }

  object ArtefactChainCellDeserializer extends CborDeserializer[ArtefactChainCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactChainCell] =
      for {
        _ <- assertOneOfRequiredTypeNames(cMap, CBORTypeNames.ArtefactChainCellTypes)
        artefact <- getRequiredReference(cMap, "artefact")
      } yield ArtefactChainCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = cMap.asStringKeyedMap
      )
  }


  object EntityChainCellDeserializer extends CborDeserializer[EntityChainCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, EntityChainCell] =
      for {
        _ <- assertOneOfRequiredTypeNames(cMap, CBORTypeNames.EntityChainCellTypes)
        entity <- getRequiredReference(cMap, "entity")
      } yield EntityChainCell(
        entity = entity,
        chain = getOptionalReference(cMap, "chain"),
        meta = cMap.asStringKeyedMap
      )
  }

  object JournalEntryDeserializer extends CborDeserializer[JournalEntry] {
    def fromCMap(cMap: CMap): Xor[DeserializationError, JournalEntry] =
      for {
        typeName <- getTypeName(cMap)
        entry <- typeName match {
          case CBORTypeNames.CanonicalEntry => CanonicalEntryDeserializer.fromCMap(cMap)
          case CBORTypeNames.ChainEntry => ChainEntryDeserializer.fromCMap(cMap)
          case _ => Xor.left(UnknownObjectType(typeName))
        }
      } yield entry


    def journalEntriesFromCArray(cArray: CArray)
    : Xor[DeserializationError, Array[JournalEntry]] = {
      val entryCMaps = cArray.items
        .flatMap(v => Try(v.asInstanceOf[CMap]).toOption)

      val entryXors: List[Xor[DeserializationError, JournalEntry]] =
        entryCMaps.map(JournalEntryDeserializer.fromCMap)

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
  }

  object JournalBlockDeserializer extends CborDeserializer[JournalBlock]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, JournalBlock] =
      for {
        _ <- assertRequiredTypeName(cMap, CBORTypeNames.JournalBlock)
        index <- getRequired[CInt](cMap, "index").map(_.num)
        entriesCArray <- getRequired[CArray](cMap, "entries")
        entries <- JournalEntryDeserializer.journalEntriesFromCArray(entriesCArray)
      } yield
        JournalBlock(
          index = index,
          chain = getOptionalReference(cMap, "chain"),
          entries = entries
        )
  }

  object CanonicalEntryDeserializer extends CborDeserializer[CanonicalEntry]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, CanonicalEntry] =
      for {
        index <- getRequired[CInt](cMap, "index").map(_.num)
        ref <- getRequiredReference(cMap, "ref")
      } yield CanonicalEntry(index, ref)
  }


  object ChainEntryDeserializer extends CborDeserializer[ChainEntry]
  {
    val CBORTypeName = CBORTypeNames.ChainEntry

    def fromCMap(cMap: CMap): Xor[DeserializationError, ChainEntry] =
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
  }


  object DummyReferenceDeserializer extends CborDeserializer[DummyReference]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, DummyReference] =
      for {
        linkString <- getRequired[CString](cMap, "@link").map(_.string)
        seqno <- Xor.catchNonFatal {
          linkString.substring("dummy@".length).toInt
        }.leftMap(_ => ReferenceDecodingFailed(s"Unable to parse int from $linkString"))
      } yield new DummyReference(seqno)
  }

  def assertRequiredTypeName(cMap: CMap, typeName: String)
  : Xor[DeserializationError, Unit] = {
    getTypeName(cMap)
      .flatMap { name =>
        if (name == typeName) {
          Xor.right({})
        } else {
          Xor.left(UnknownObjectType(name))
        }
      }
  }

  def assertOneOfRequiredTypeNames(cMap: CMap, typeNames: Set[String])
  : Xor[DeserializationError, Unit] = {
    getTypeName(cMap)
      .flatMap { name =>
        if (typeNames.contains(name)) {
          Xor.right({})
        } else {
          Xor.left(UnknownObjectType(name))
        }
      }
  }

  def getTypeName(cMap: CMap): Xor[TypeNameNotFound, String] =
    getRequired[CString](cMap, "type")
      .map(_.string)
      .leftMap(_ => TypeNameNotFound())


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


  def referenceFromCMap(cMap: CMap): Xor[DeserializationError, Reference] =
    getRequired[CMap](cMap, "@link")
      .flatMap { linkVal: CValue =>
        linkVal match {
          case CString(s) if s.startsWith("dummy@") => {
            DummyReferenceDeserializer.fromCMap(cMap)
          }
          case _ =>
            Xor.left(ReferenceDecodingFailed(s"Unknown link value $linkVal"))
        }
      }

}
