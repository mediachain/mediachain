package io.mediachain.transactor

import io.mediachain.multihash.MultiHash


object CborSerialization {

  import cats.data.Xor
  import io.mediachain.transactor.Dummies.DummyReference
  import io.mediachain.transactor.Types._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.util.cbor.CborCodec

  import scala.util.Try

  type DeserializerMap = Map[CborTypeName, CborDeserializer[CborSerializable]]

  /**
    * Try to deserialize a `DataObject` from a cbor `CValue`
    *
    * @param cValue the `CValue` to decode
    * @return a `DataObject`, or `DeserializationError` on failure
    */
  def dataObjectFromCbor(cValue: CValue)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, DataObject] =
    fromCbor(cValue)(deserializers) match {
      case Xor.Left(err) => Xor.left(err)
      case Xor.Right(dataObject: DataObject) => Xor.right(dataObject)
      case Xor.Right(unknownObject) =>
        Xor.left(UnexpectedCborType(
          s"Expected DataObject, but got ${unknownObject.getClass.getTypeName}"
        ))
    }

  /**
    * Try to deserialize a `JournalEntry` from a cbor `CValue`
    *
    * @param cValue the `CValue` to decode
    * @return a `JournalEntry`, or DeserializationError on failure
    */
  def journalEntryFromCbor(cValue: CValue)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, JournalEntry] =
    fromCbor(cValue)(deserializers) match {
      case Xor.Left(err) => Xor.left(err)
      case Xor.Right(journalEntry: JournalEntry) => Xor.right(journalEntry)
      case Xor.Right(unknownObject) =>
        Xor.left(UnexpectedCborType(
          s"Expected JournalEntry, but got ${unknownObject.getClass.getTypeName}"
        ))
    }

  /**
    * Try to deserialize some `CborSerializable` object from a byte array.
    *
    * @param bytes an array of (presumably) cbor-encoded data
    * @return a `CborSerializable` object, or a `DeserializationError` on failure
    */
  def fromCborBytes(bytes: Array[Byte])
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, CborSerializable] =
    CborCodec.decode(bytes) match {
      case (_: CTag) :: (taggedValue: CValue) :: _ => fromCbor(taggedValue)(deserializers)
      case (cValue: CValue) :: _ => fromCbor(cValue)(deserializers)
      case Nil => Xor.left(CborDecodingFailed())
    }

  /**
    * Try to deserialize a `CborSerializable` object from a cbor `CValue`
    *
    * @param cValue the `CValue` to decode
    * @return a `CborSerializable` object, or a `DeserializationError` on failure
    */
  def fromCbor(cValue: CValue)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, CborSerializable] =
    cValue match {
      case (cMap: CMap) => fromCMap(cMap)(deserializers)
      case _ => Xor.left(UnexpectedCborType(
        s"Expected CBOR map, but received ${cValue.getClass.getName}"
      ))
    }

  /**
    * Try to deserialize some `CborSerializable` object from a cbor `CMap`.
    *
    * @param cMap a cbor map representing the object to decode
    * @return a `CborSerializable` object, or a `DeserializationError` on failure
    */
  def fromCMap(cMap: CMap)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, CborSerializable] = {
    for {
      typeName <- getTypeName(cMap)
      deserializer <- Xor.fromOption(
        deserializers.get(typeName),
        UnexpectedObjectType(typeName.toString)
      )
      value <- deserializer.fromCMap(cMap)
    } yield value
  }


  sealed trait CborTypeName {
    val stringValue: String
    def cborString: CString = CString(stringValue)
    override def toString = stringValue
  }

  object CborTypeNames {
    def fromString(string: String): Xor[UnexpectedObjectType, CborTypeName] =
      string match {
        case Entity.stringValue => Xor.right(Entity)
        case Artefact.stringValue => Xor.right(Artefact)
        case EntityChainCell.stringValue => Xor.right(EntityChainCell)
        case ArtefactChainCell.stringValue => Xor.right(ArtefactChainCell)
        case CanonicalEntry.stringValue => Xor.right(CanonicalEntry)
        case ChainEntry.stringValue => Xor.right(ChainEntry)
        case JournalBlock.stringValue => Xor.right(JournalBlock)

        case _ => Xor.left(UnexpectedObjectType(string))
      }

    case object Entity extends CborTypeName {
      val stringValue = "entity"
    }
    case object Artefact extends CborTypeName {
      val stringValue = "artefact"
    }
    case object EntityChainCell extends CborTypeName {
      val stringValue = "entityChainCell"
    }
    case object ArtefactChainCell extends CborTypeName {
      val stringValue = "artefactChainCell"
    }
    case object CanonicalEntry extends CborTypeName {
      val stringValue = "insert"
    }
    case object ChainEntry extends CborTypeName {
      val stringValue = "update"
    }
    case object JournalBlock extends CborTypeName {
      val stringValue = "journalBlock"
    }

    val ArtefactChainCellTypes: Set[CborTypeName] = Set(
      ArtefactChainCell
    )

    val EntityChainCellTypes: Set[CborTypeName] = Set(
      EntityChainCell
    )
  }



  // TODO: create multiple deserializer maps for different contexts
  // e.g. DataStore should deserialize chain cells to specific subtypes, etc
  val transactorDeserializers: DeserializerMap =
    Map(
      CborTypeNames.Entity -> EntityDeserializer,
      CborTypeNames.Artefact -> ArtefactDeserializer,
      CborTypeNames.EntityChainCell -> EntityChainCellDeserializer,
      CborTypeNames.ArtefactChainCell -> ArtefactChainCellDeserializer,
      CborTypeNames.CanonicalEntry -> CanonicalEntryDeserializer,
      CborTypeNames.ChainEntry -> ChainEntryDeserializer,
      CborTypeNames.JournalBlock -> JournalBlockDeserializer
    )

  val defaultDeserializers = transactorDeserializers

  /**
    * Trait for objects that can be serialized to cbor.
    */
  trait CborSerializable {
    val CBORType: Option[CborTypeName]

    def toCborBytes: Array[Byte] = CborCodec.encode(toCbor)

    def toCbor: CValue =
      toCMapWithDefaults(Map.empty, Map.empty)

    def toCMapWithDefaults(defaults: Map[String, CValue],
      optionals: Map[String, Option[CValue]])
    : CMap = {
      val withType = ("type" -> CBORType.map(_.cborString)) :: optionals.toList
      val merged = defaults ++ withType.flatMap {
        case (_, None) => List.empty
        case (k, Some(v)) => List(k -> v)
      }

      CMap.withStringKeys(merged.toList)
    }
  }

  /**
    * Trait for an object that can deserialize a value of type `T` from
    * a cbor `CValue`.
    *
    * @tparam T the type of object to decode. Must be `CborSerializable`.
    */
  trait CborDeserializer[+T <: CborSerializable] {
    def fromCMap(cMap: CMap): Xor[DeserializationError, T]

    def fromCValue(cValue: CValue): Xor[DeserializationError, T] =
      cValue match {
        case (cMap: CMap) => fromCMap(cMap)
        case _ => Xor.left(UnexpectedCborType(
          s"Expected CBOR map, but received ${cValue.getClass.getName}"
        ))
      }
  }


  /**
    * Indicates that deserialization from cbor failed
    */
  sealed trait DeserializationError

  case class CborDecodingFailed() extends DeserializationError

  case class UnexpectedCborType(message: String) extends DeserializationError

  case class ReferenceDecodingFailed(message: String) extends DeserializationError

  case class TypeNameNotFound() extends DeserializationError

  case class UnexpectedObjectType(typeName: String) extends DeserializationError

  case class RequiredFieldNotFound(fieldName: String) extends DeserializationError


  object EntityDeserializer extends CborDeserializer[Entity]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Entity] =
      assertRequiredTypeName(cMap, CborTypeNames.Entity).map { _ =>
        Entity(cMap.asStringKeyedMap)
      }
  }

  object ArtefactDeserializer extends CborDeserializer[Artefact]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Artefact] =
      assertRequiredTypeName(cMap, CborTypeNames.Artefact).map { _ =>
        Artefact(cMap.asStringKeyedMap)
      }
  }

  object ArtefactChainCellDeserializer extends CborDeserializer[ArtefactChainCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactChainCell] =
      for {
        _ <- assertOneOfRequiredTypeNames(cMap, CborTypeNames.ArtefactChainCellTypes)
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
        _ <- assertOneOfRequiredTypeNames(cMap, CborTypeNames.EntityChainCellTypes)
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
          case CborTypeNames.CanonicalEntry => CanonicalEntryDeserializer.fromCMap(cMap)
          case CborTypeNames.ChainEntry => ChainEntryDeserializer.fromCMap(cMap)
          case _ => Xor.left(UnexpectedObjectType(typeName.toString))
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
        _ <- assertRequiredTypeName(cMap, CborTypeNames.JournalBlock)
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


  object MultihashReferenceDeserializer extends CborDeserializer[MultihashReference]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, MultihashReference] =
      for {
        hashBytes <- getRequired[CBytes](cMap, "@link").map(_.bytes)
        multihash <- MultiHash.fromBytes(hashBytes)
          .leftMap(err => ReferenceDecodingFailed(s"Multihash decoding failed: $err"))
      } yield MultihashReference(multihash)
  }


  /**
    * Assert that the cbor map contains a `type` field with the given value.
    *
    * @param cMap a cbor `CMap` to check the type of
    * @param typeName the required value of the `type` field
    * @return `Unit` on success, or `DeserializationError` if there is no
    *        `type` field, or if the value is incorrect
    */
  def assertRequiredTypeName(cMap: CMap, typeName: CborTypeName)
  : Xor[DeserializationError, Unit] = {
    if (getTypeName(cMap).exists(_ == typeName)) {
      Xor.right({})
    } else {
      Xor.left(UnexpectedObjectType(typeName.toString))
    }

  }

  /**
    * Assert that the cbor map contains a `type` field whose value is one of
    * the members of the `typeNames` set.
    *
    * @param cMap a cbor `CMap` to check the type of
    * @param typeNames a set of valid values for the `type` field
    * @return `Unit` on success, or `DeserializationError` if there is no
    *        `type` field, or if the value is not contained in the
    *        `typeNames` set
    */
  def assertOneOfRequiredTypeNames(cMap: CMap, typeNames: Set[CborTypeName])
  : Xor[DeserializationError, Unit] =
    for {
      typeName <- getTypeName(cMap)
      result <- if (typeNames.contains(typeName)) {
        Xor.right({})
      } else {
        Xor.left(UnexpectedObjectType(typeName.toString))
      }
    } yield result


  /**
    * Get the value of the `type` field from the given cbor map
    *
    * @param cMap a cbor `CMap` to get the type name from
    * @return the value of the `type` field, or a `DeserializationError` error if
    *         no `type` field exists, or its value is not a valid type name
    */
  def getTypeName(cMap: CMap): Xor[DeserializationError, CborTypeName] =
    getRequired[CString](cMap, "type")
      .bimap(_ => TypeNameNotFound(), cString => cString.string)
      .flatMap(CborTypeNames.fromString)


  /**
    * Get the value of a required field in a cbor map.
    * 
    * @param cMap a cbor `CMap` to pull the field from
    * @param fieldName the name of the field
    * @tparam T the type of `CValue` to return
    * @return the value of the field, or a `RequiredFieldNotFound` error if
    *         the field doesn't exist
    */
  def getRequired[T <: CValue](cMap: CMap, fieldName: String)
  : Xor[RequiredFieldNotFound, T] = Xor.fromOption(
    cMap.getAs[T](fieldName),
    RequiredFieldNotFound(fieldName)
  )


  /**
    * Get a required field whose value is an encoded `Reference` type.
    *
    * @param cMap a cbor `CMap` to pull the reference field from
    * @param fieldName the name of the field
    * @return the deserialized `Reference`, or a `DeserializationError` if
    *         the field doesn't exist or can't be decoded as a reference
    */
  def getRequiredReference(cMap: CMap, fieldName: String)
  : Xor[DeserializationError, Reference] =
    getRequired[CMap](cMap, fieldName)
    .flatMap(referenceFromCMap)

  /**
    * Get an optional `Reference` value from the cbor map
    *
    * @param cMap a cbor `CMap` to pull the reference field from.
    * @param fieldName the name of the field
    * @return Some[Reference] if the field exists and can be decoded,
    *         None otherwise
    */
  def getOptionalReference(cMap: CMap, fieldName: String): Option[Reference] =
    cMap.getAs[CMap](fieldName)
      .flatMap(referenceFromCMap(_).toOption)


  /**
    * Try to decode a `Reference` from a cbor map.
    *
    * Only MultihashReferences are supported for deserialization;
    * DummyReferences are for testing only, and use POJO serialization.
    *
    * @param cMap the cbor `CMap` to decode as a `Reference`
    * @return the decoded `Reference`, or a `DeserializationError` if decoding
    *         fails
    */
  def referenceFromCMap(cMap: CMap): Xor[DeserializationError, Reference] =
    MultihashReferenceDeserializer.fromCMap(cMap)

}
