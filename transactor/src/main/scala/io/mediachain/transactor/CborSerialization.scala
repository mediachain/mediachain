package io.mediachain.transactor





object CborSerialization {

  import cats.data.Xor
  import io.mediachain.transactor.Dummies.DummyReference
  import io.mediachain.transactor.Types._
  import io.mediachain.util.cbor.CborAST._
  import io.mediachain.util.cbor.CborCodec

  import scala.util.Try
      case Xor.Right(dataObject: DataObject) => Xor.right(dataObject)
      case Xor.Right(unknownObject) =>
        Xor.left(UnexpectedCborType(
          s"Expected DataObject, but got ${unknownObject.getClass.getTypeName}"
      case Xor.Right(journalEntry: JournalEntry) => Xor.right(journalEntry)
      case Xor.Right(unknownObject) =>
        Xor.left(UnexpectedCborType(
          s"Expected JournalEntry, but got ${unknownObject.getClass.getTypeName}"
      case (cValue: CValue) :: _ => fromCbor(cValue)
      case _ => Xor.left(UnexpectedCborType(
        s"Expected CBOR map, but received ${cValue.getClass.getName}"
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
      value <- deserializer.fromCMap(cMap)
    } yield value
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
      .flatMap { name =>
        if (name == typeName) {
          Xor.right({})
        } else {
          Xor.left(UnknownObjectType(name))
        }
      .flatMap { name =>
        if (typeNames.contains(name)) {
          Xor.right({})
        } else {
          Xor.left(UnknownObjectType(name))
        }
    RequiredFieldNotFound(fieldName)
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
