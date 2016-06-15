package io.mediachain.protocol

import io.mediachain.multihash.MultiHash

object CborSerialization {

  import cats.data.Xor
  import io.mediachain.protocol.Datastore._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.util.cbor.CborCodec

  import scala.util.Try

  /**
    * A mapping of `MediachainType` to the `CborDeserializer` to use when
    * decoding objects of that type.
    *
    * Used to allow the transactors to deserialize chain cells into the most
    * generic representation, while allowing other peers to deserialize into
    * specific subtypes.
    *
    * Defaults to deserializing into specific subtypes; to override this
    * behavior and deserialize into generic `EntityChainCell`s and
    * `ArtefactChainCell`s, make an implicit `DeserializerMap` in the scope
    * where you call the various `fromCbor` methods.
    *
    * e.g:
    * ```
    * implicit val deserializerMap = CborSerialization.transactorDeserializers
    *
    * val fooCell = fromCborBytes(fooCellBytes)
    * ```
    */
  type DeserializerMap = Map[MediachainType, CborDeserializer[CborSerializable]]

  /**
    * Try to deserialize a `DataObject` from a cbor `CValue`
 *
    * @param cValue the `CValue` to deserialize from
    * @return the decoded `DataObject`, or a `DeserializationError` on failure
    */
  def dataObjectFromCValue(cValue: CValue)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, DataObject] =
    fromCbor[DataObject](cValue)(deserializers)


  /**
    * Try to deserialize a `DataObject` from a cbor-encoded byte array
 *
    * @param bytes the byte array to deserialize from
    * @return the decoded `DataObject`, or a `DeserializationError` on failure
    */
  def dataObjectFromCborBytes(bytes: Array[Byte])
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, DataObject] =
    fromCborBytes[DataObject](bytes)(deserializers)


  /**
    * Try to deserialize a `JournalEntry` from a cbor `CValue`
 *
    * @param cValue the `CValue` to deserialize from
    * @return the decoded `JournalEntry`, or a `DeserializationError` on failure
    */
  def journalEntryFromCValue(cValue: CValue)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, JournalEntry] =
    fromCbor[JournalEntry](cValue)(deserializers)


  /**
    * Try to deserialize a `JournalEntry` from a cbor-encoded byte array
 *
    * @param bytes the byte array to deserialize from
    * @return the decoded `JournalEntry`, or a `DeserializationError` on failure
    */
  def journalEntryFromCborBytes(bytes: Array[Byte])
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, JournalEntry] =
    fromCborBytes[JournalEntry](bytes)(deserializers)


  /**
    * Try to deserialize a `Reference` from a cbor-encoded byte array
    * @param bytes the byte array to deserialize from
    * @return the decoded `Reference`, or a `DeserializationError` on failure
    */
  def referenceFromCborBytes(bytes: Array[Byte])
  : Xor[DeserializationError, Reference] = {
    val refMapXor = CborCodec.decode(bytes) match {
      case (_: CTag) :: (refMap: CMap) :: _ => Xor.right(refMap)
      case (refMap: CMap) :: _ => Xor.right(refMap)
      case _ => Xor.left(CborDecodingFailed())
    }

    refMapXor.flatMap(ReferenceDeserializer.fromCMap)
  }

  /**
    * Try to deserialize some `CborSerializable` object from a byte array.
    *
    * @param bytes an array of (presumably) cbor-encoded data
    * @return a `CborSerializable` object, or a `DeserializationError` on failure
    */
  def fromCborBytes[T <: CborSerializable](bytes: Array[Byte])
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, T] =
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
  def fromCbor[T <: CborSerializable](cValue: CValue)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, T] =
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
  def fromCMap[T <: CborSerializable](cMap: CMap)
    (implicit deserializers: DeserializerMap = defaultDeserializers)
  : Xor[DeserializationError, T] = {
    for {
      typeName <- getTypeName(cMap)
      deserializer <- Xor.fromOption(
        deserializers.get(typeName),
        UnexpectedObjectType(typeName.toString)
      )
      typedDeserializer <- Xor.fromTry(
        Try(deserializer.asInstanceOf[CborDeserializer[T]])
      ).leftMap(ex => IncompatibleDeserializerType(
        s"Deserializer of type ${deserializer.getClass.getTypeName} cannot " +
        s"produce a value of required type: $ex"
      ))
      value <- typedDeserializer.fromCMap(cMap)
    } yield value
  }


  sealed trait MediachainType {
    val stringValue: String
    def cborString: CString = CString(stringValue)
    override def toString = stringValue
  }

  object MediachainTypes {
    def fromString(string: String): Xor[UnexpectedObjectType, MediachainType] =
      string match {
        case Entity.stringValue => Xor.right(Entity)
        case Artefact.stringValue => Xor.right(Artefact)
        case EntityChainCell.stringValue => Xor.right(EntityChainCell)
        case EntityUpdateCell.stringValue => Xor.right(EntityUpdateCell)
        case EntityLinkCell.stringValue => Xor.right(EntityLinkCell)
        case ArtefactChainCell.stringValue => Xor.right(ArtefactChainCell)
        case ArtefactUpdateCell.stringValue => Xor.right(ArtefactUpdateCell)
        case ArtefactLinkCell.stringValue => Xor.right(ArtefactLinkCell)
        case ArtefactCreationCell.stringValue => Xor.right(ArtefactCreationCell)
        case ArtefactDerivationCell.stringValue => Xor.right(ArtefactDerivationCell)
        case ArtefactOwnershipCell.stringValue => Xor.right(ArtefactOwnershipCell)
        case ArtefactReferenceCell.stringValue => Xor.right(ArtefactReferenceCell)
        case CanonicalEntry.stringValue => Xor.right(CanonicalEntry)
        case ChainEntry.stringValue => Xor.right(ChainEntry)
        case JournalBlock.stringValue => Xor.right(JournalBlock)
        case JournalBlockArchive.stringValue => Xor.right(JournalBlockArchive)

        case _ => Xor.left(UnexpectedObjectType(string))
      }

    def fromCValue(cValue: CValue): Xor[DeserializationError, MediachainType] =
      cValue match {
        case CString(string) => fromString(string)
        case x => Xor.left(
          UnexpectedCborType(s"Expected CBOR string, but found ${x.getClass.getTypeName}"))
      }

    case object Entity extends MediachainType {
      val stringValue = "entity"
    }
    case object Artefact extends MediachainType {
      val stringValue = "artefact"
    }
    case object EntityChainCell extends MediachainType {
      val stringValue = "entityChainCell"
    }
    case object EntityUpdateCell extends MediachainType {
      val stringValue = "entityUpdate"
    }
    case object EntityLinkCell extends MediachainType {
      val stringValue = "entityLink"
    }
    case object ArtefactChainCell extends MediachainType {
      val stringValue = "artefactChainCell"
    }
    case object ArtefactUpdateCell extends MediachainType {
      val stringValue = "artefactUpdate"
    }
    case object ArtefactLinkCell extends MediachainType {
      val stringValue = "artefactLink"
    }
    case object ArtefactCreationCell extends MediachainType {
      val stringValue = "artefactCreatedBy"
    }
    case object ArtefactDerivationCell extends MediachainType {
      val stringValue = "artefactDerivedBy"
    }
    case object ArtefactOwnershipCell extends MediachainType {
      val stringValue = "artefactRightsOwnedBy"
    }
    case object ArtefactReferenceCell extends MediachainType {
      val stringValue = "artefactReferencedBy"
    }
    case object CanonicalEntry extends MediachainType {
      val stringValue = "insert"
    }
    case object ChainEntry extends MediachainType {
      val stringValue = "update"
    }
    case object JournalBlock extends MediachainType {
      val stringValue = "journalBlock"
    }
    case object JournalBlockArchive extends MediachainType {
      val stringValue = "journalBlockArchive"
    }

    val ArtefactChainCellTypes: Set[MediachainType] = Set(
      ArtefactChainCell, ArtefactUpdateCell, ArtefactLinkCell, ArtefactCreationCell,
      ArtefactDerivationCell, ArtefactOwnershipCell, ArtefactReferenceCell
    )

    val EntityChainCellTypes: Set[MediachainType] = Set(
      EntityChainCell, EntityUpdateCell, EntityLinkCell
    )
  }



  // TODO: create multiple deserializer maps for different contexts
  // e.g. DataStore should deserialize chain cells to specific subtypes, etc
  val transactorDeserializers: DeserializerMap =
    Map(
      MediachainTypes.Entity -> EntityDeserializer,
      MediachainTypes.Artefact -> ArtefactDeserializer,

      MediachainTypes.EntityChainCell -> EntityChainCellDeserializer,
      MediachainTypes.EntityUpdateCell -> EntityChainCellDeserializer,
      MediachainTypes.EntityLinkCell -> EntityChainCellDeserializer,

      MediachainTypes.ArtefactChainCell -> ArtefactChainCellDeserializer,
      MediachainTypes.ArtefactUpdateCell -> ArtefactChainCellDeserializer,
      MediachainTypes.ArtefactLinkCell -> ArtefactChainCellDeserializer,
      MediachainTypes.ArtefactCreationCell -> ArtefactChainCellDeserializer,
      MediachainTypes.ArtefactDerivationCell -> ArtefactChainCellDeserializer,
      MediachainTypes.ArtefactOwnershipCell -> ArtefactChainCellDeserializer,
      MediachainTypes.ArtefactReferenceCell -> ArtefactChainCellDeserializer,

      MediachainTypes.CanonicalEntry -> CanonicalEntryDeserializer,
      MediachainTypes.ChainEntry -> ChainEntryDeserializer,
      MediachainTypes.JournalBlock -> JournalBlockDeserializer,
      MediachainTypes.JournalBlockArchive -> JournalBlockArchiveDeserializer
    )

  val datastoreDeserializers: DeserializerMap =
    transactorDeserializers ++ Seq(
      MediachainTypes.EntityUpdateCell -> EntityUpdateCellDeserializer,
      MediachainTypes.EntityLinkCell -> EntityLinkCellDeserializer,

      MediachainTypes.ArtefactUpdateCell -> ArtefactUpdateCellDeserializer,
      MediachainTypes.ArtefactLinkCell -> ArtefactLinkCellDeserializer,
      MediachainTypes.ArtefactCreationCell -> ArtefactCreationCellDeserializer,
      MediachainTypes.ArtefactDerivationCell -> ArtefactDerivationCellDeserializer,
      MediachainTypes.ArtefactOwnershipCell -> ArtefactOwnershipCellDeserializer,
      MediachainTypes.ArtefactReferenceCell -> ArtefactReferenceCellDeserializer
    )

  val defaultDeserializers = datastoreDeserializers

  /**
    * Trait for objects that can be serialized to cbor.
    */
  trait CborSerializable {
    val mediachainType: Option[MediachainType]

    def toCborBytes: Array[Byte] = CborCodec.encode(toCbor)

    def toCbor: CValue =
      toCMapWithDefaults(Map.empty, Map.empty)

    def toCMapWithDefaults(defaults: Map[String, CValue],
      optionals: Map[String, Option[CValue]])
    : CMap = {
      val withType = ("type" -> mediachainType.map(_.cborString)) :: optionals.toList
      val merged = defaults ++ withType.flatMap {
        case (_, None) => List.empty
        case (k, Some(v)) => List(k -> v)
      }

      CMap.withStringKeys(merged.toList)
    }
    
    def toCMapWithMeta(
      defaults: Map[String, CValue],
      optionals: Map[String, Option[CValue]],
      meta: Map[String, CValue],
      metaSource: Option[Reference])
    : CMap =
      toCMapWithDefaults(defaults + ("meta" -> CMap.withStringKeys(meta.toList)),
                         optionals + ("metaSource" -> metaSource.map(_.toCbor)))
    
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
  sealed trait DeserializationError {
    def message: String
  }

  case class CborDecodingFailed() extends DeserializationError {
    def message = "CBOR Decoding error"
  }

  case class UnexpectedCborType(message: String) extends DeserializationError

  case class ReferenceDecodingFailed(message: String) extends DeserializationError

  case class TypeNameNotFound() extends DeserializationError {
    def message = "Unknown Type name"
  }

  case class UnexpectedObjectType(typeName: String) extends DeserializationError {
    def message = "Unexpected type " + typeName
  }

  case class RequiredFieldNotFound(fieldName: String) extends DeserializationError {
    def message = "Missing field " + fieldName
  }

  case class IncompatibleDeserializerType(message: String) extends DeserializationError

  object EntityDeserializer extends CborDeserializer[Entity]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Entity] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.Entity)
        meta <- getRequired[CMap](cMap, "meta")
      } yield Entity(meta.asStringKeyedMap, 
                     getOptionalReference(cMap, "metaSource"))
  }

  object ArtefactDeserializer extends CborDeserializer[Artefact]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Artefact] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.Artefact)
        meta <- getRequired[CMap](cMap, "meta")
      } yield Artefact(meta.asStringKeyedMap,
                       getOptionalReference(cMap, "metaSource"))
  }

  object ArtefactChainCellDeserializer extends CborDeserializer[ArtefactChainCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactChainCell] =
      for {
        _ <- assertOneOfRequiredTypeNames(cMap, MediachainTypes.ArtefactChainCellTypes)
        artefact <- getRequiredReference(cMap, "ref")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactChainCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource")
      )
  }

  object ArtefactUpdateCellDeserializer extends CborDeserializer[ArtefactUpdateCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactUpdateCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.ArtefactUpdateCell)
        artefact <- getRequiredReference(cMap, "ref")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactUpdateCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource")
      )
  }

  object ArtefactLinkCellDeserializer extends CborDeserializer[ArtefactLinkCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactLinkCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.ArtefactLinkCell)
        artefact <- getRequiredReference(cMap, "ref")
        artefactLink <- getRequiredReference(cMap, "artefactLink")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactLinkCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource"),
        artefactLink = artefactLink
      )
  }

  object ArtefactCreationCellDeserializer extends CborDeserializer[ArtefactCreationCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactCreationCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.ArtefactCreationCell)
        artefact <- getRequiredReference(cMap, "ref")
        entity <- getRequiredReference(cMap, "entity")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactCreationCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource"),
        entity = entity
      )
  }

  object ArtefactDerivationCellDeserializer extends CborDeserializer[ArtefactDerivationCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactDerivationCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.ArtefactDerivationCell)
        artefact <- getRequiredReference(cMap, "ref")
        artefactLink <- getRequiredReference(cMap, "artefactLink")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactDerivationCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource"),
        artefactLink = artefactLink
      )
  }

  object ArtefactOwnershipCellDeserializer extends CborDeserializer[ArtefactOwnershipCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactOwnershipCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.ArtefactOwnershipCell)
        artefact <- getRequiredReference(cMap, "ref")
        entity <- getRequiredReference(cMap, "entity")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactOwnershipCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource"),
        entity = entity
      )
  }

  object ArtefactReferenceCellDeserializer extends CborDeserializer[ArtefactReferenceCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, ArtefactReferenceCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.ArtefactReferenceCell)
        artefact <- getRequiredReference(cMap, "ref")
        meta <- getRequired[CMap](cMap, "meta")
      } yield ArtefactReferenceCell(
        artefact = artefact,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource"),
        entity = getOptionalReference(cMap, "entity")
      )
  }

  object EntityChainCellDeserializer extends CborDeserializer[EntityChainCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, EntityChainCell] =
      for {
        _ <- assertOneOfRequiredTypeNames(cMap, MediachainTypes.EntityChainCellTypes)
        entity <- getRequiredReference(cMap, "ref")
        meta <- getRequired[CMap](cMap, "meta")
      } yield EntityChainCell(
        entity = entity,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource")
      )
  }

  object EntityUpdateCellDeserializer extends CborDeserializer[EntityUpdateCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, EntityUpdateCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.EntityUpdateCell)
        entity <- getRequiredReference(cMap, "ref")
        meta <- getRequired[CMap](cMap, "meta")
      } yield EntityUpdateCell(
        entity = entity,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource")
      )
  }

  object EntityLinkCellDeserializer extends CborDeserializer[EntityLinkCell]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, EntityLinkCell] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.EntityLinkCell)
        entity <- getRequiredReference(cMap, "ref")
        entityLink <- getRequiredReference(cMap, "entityLink")
        meta <- getRequired[CMap](cMap, "meta")
      } yield EntityLinkCell(
        entity = entity,
        chain = getOptionalReference(cMap, "chain"),
        meta = meta.asStringKeyedMap,
        metaSource = getOptionalReference(cMap, "metaSource"),
        entityLink = entityLink
      )
  }

  object JournalEntryDeserializer extends CborDeserializer[JournalEntry] {
    def fromCMap(cMap: CMap): Xor[DeserializationError, JournalEntry] =
      for {
        typeName <- getTypeName(cMap)
        entry <- typeName match {
          case MediachainTypes.CanonicalEntry => CanonicalEntryDeserializer.fromCMap(cMap)
          case MediachainTypes.ChainEntry => ChainEntryDeserializer.fromCMap(cMap)
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
        _ <- assertRequiredTypeName(cMap, MediachainTypes.JournalBlock)
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

  object JournalBlockArchiveDeserializer extends CborDeserializer[JournalBlockArchive]
  {
    import scala.annotation.tailrec

    def fromCMap(cMap: CMap): Xor[DeserializationError, JournalBlockArchive] =
      for {
        _ <- assertRequiredTypeName(cMap, MediachainTypes.JournalBlockArchive)
        ref <- getRequiredReference(cMap, "ref")
        xblock <- getRequired[CMap](cMap, "block")
        block <- JournalBlockDeserializer.fromCMap(xblock)
        xdata <- getRequired[CArray](cMap, "data")
        data <- dataFromCbor(xdata)
      } yield
        JournalBlockArchive(
          ref = ref,
          block = block,
          data = data
        )
    
    def dataFromCbor(records: CArray) = {
      @tailrec def loop(rest: List[CValue], map: Map[Reference, Array[Byte]])
      : Xor[DeserializationError, Map[Reference, Array[Byte]]] = {
        rest match {
          case CArray(List(refCbor: CMap, data: CBytes)) :: rest =>
            ReferenceDeserializer.fromCMap(refCbor) match {
              case Xor.Right(ref) =>
                loop(rest, map + (ref -> data.bytes))
              case err@Xor.Left(_) => err
            }
            
          case hd :: _ =>
            Xor.Left(UnexpectedObjectType(s"Archive data decoding failed; unexpected object: $hd"))
            
          case Nil => Xor.Right(map)
        }
      }
      
      loop(records.items, Map())
    }
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

  object ReferenceDeserializer extends CborDeserializer[Reference]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, Reference] = {
      if (cMap.contains("@link")) {
        MultihashReferenceDeserializer.fromCMap(cMap)
      } else if (cMap.contains("@dummy")) {
        DummyReferenceDeserializer.fromCMap(cMap)
      } else {
        Xor.Left(ReferenceDecodingFailed("Unknown reference type"))
      }
    }
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

  object DummyReferenceDeserializer extends CborDeserializer[DummyReference]
  {
    def fromCMap(cMap: CMap): Xor[DeserializationError, DummyReference] =
      for {
        index <- getRequired[CInt](cMap, "@dummy")
      } yield DummyReference(index.num.toInt)
  }


  /**
    * Assert that the cbor map contains a `type` field with the given value.
    *
    * @param cMap a cbor `CMap` to check the type of
    * @param typeName the required value of the `type` field
    * @return `Unit` on success, or `DeserializationError` if there is no
    *        `type` field, or if the value is incorrect
    */
  def assertRequiredTypeName(cMap: CMap, typeName: MediachainType)
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
  def assertOneOfRequiredTypeNames(cMap: CMap, typeNames: Set[MediachainType])
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
  def getTypeName(cMap: CMap): Xor[DeserializationError, MediachainType] =
    getRequired[CString](cMap, "type")
      .bimap(_ => TypeNameNotFound(), cString => cString.string)
      .flatMap(MediachainTypes.fromString)


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
    * @param cMap the cbor `CMap` to decode as a `Reference`
    * @return the decoded `Reference`, or a `DeserializationError` if decoding
    *         fails
    */
  def referenceFromCMap(cMap: CMap): Xor[DeserializationError, Reference] =
    ReferenceDeserializer.fromCMap(cMap)

}
