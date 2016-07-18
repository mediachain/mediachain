package io.mediachain.copycat

import io.atomix.catalyst.serializer.{SerializationException, Serializer, TypeSerializer}
import io.atomix.catalyst.buffer.{BufferInput, BufferOutput}
import cats.data.Xor
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.CborSerialization
import io.mediachain.copycat.StateMachine._
import io.mediachain.protocol.Transactor._
import io.mediachain.multihash.MultiHash

object Serializers {
  val klasses = List(classOf[JournalCurrentBlock],
                     classOf[JournalCommitEvent],
                     classOf[JournalBlockEvent],
                     classOf[JournalState])
  
  def register(serializer: Serializer) {
    klasses.foreach(serializer.register(_))
    serializer.register(classOf[JournalBlock], classOf[JournalBlockSerializer])
    serializer.register(classOf[JournalInsert], classOf[JournalInsertSerializer])
    serializer.register(classOf[JournalUpdate], classOf[JournalUpdateSerializer])
    serializer.register(classOf[JournalLookup], classOf[JournalLookupSerializer])
  }
  
  def readBytes(buf: BufferInput[_ <: BufferInput[_]]): Array[Byte] = {
    val len = buf.readInt()
    val bytes = new Array[Byte](len)
    buf.read(bytes)
    bytes
  }
  
  def writeBytes(buf: BufferOutput[_ <: BufferOutput[_]], bytes: Array[Byte]) {
    buf.writeInt(bytes.length)
    buf.write(bytes)
  }
  
  class JournalBlockSerializer extends TypeSerializer[JournalBlock] {
    def read(klass: Class[JournalBlock], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalBlock = {
      val bytes = readBytes(buf)
      CborSerialization.fromCborBytes[JournalBlock](bytes) match {
        case Xor.Right(block) => 
          block
        case Xor.Left(err) =>
          throw new SerializationException("Failed to deserialize JournalBlock: " + err.message)
      }
    }
    
    def write(block: JournalBlock, buf: BufferOutput[_ <: BufferOutput[_]], ser: Serializer) {
      writeBytes(buf, block.toCborBytes)
    }
  }

  class JournalInsertSerializer extends TypeSerializer[JournalInsert] {
    def read(klass: Class[JournalInsert], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalInsert = {
      val bytes = readBytes(buf)
      CborSerialization.fromCborBytes[CanonicalRecord](bytes) match {
        case Xor.Right(record) => 
          JournalInsert(record)
        case Xor.Left(err) =>
          throw new SerializationException("Failed to deserialize JournalInsert: " + err.message)
      }
    }

    def write(command: JournalInsert, buf: BufferOutput[_ <: BufferOutput[_]], ser: Serializer) {
      writeBytes(buf, command.record.toCborBytes)
    }
  }

  class JournalUpdateSerializer extends TypeSerializer[JournalUpdate] {
    def read(klass: Class[JournalUpdate], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalUpdate = {
      val refBytes = readBytes(buf)
      val cellBytes = readBytes(buf)
      
      val updateXor = for {
        ref <- CborSerialization.referenceFromCborBytes(refBytes)
        cell <- CborSerialization.fromCborBytes[ChainCell](cellBytes)
      } yield JournalUpdate(ref, cell)

      updateXor match {
        case Xor.Left(err) =>
          throw new SerializationException("Failed to deserialize JournalUpdate: " + err.message)
        case Xor.Right(obj) => obj
      }
    }

    def write(update: JournalUpdate, buf: BufferOutput[_ <: BufferOutput[_]], ser: Serializer) {
      writeBytes(buf, update.ref.toCborBytes)
      writeBytes(buf, update.cell.toCborBytes)
    }
  }

  class JournalLookupSerializer extends TypeSerializer[JournalLookup] {
    def read(klass: Class[JournalLookup], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalLookup = {
      val bytes = readBytes(buf)
      CborSerialization.referenceFromCborBytes(bytes) match {
        case Xor.Right(ref) =>
          JournalLookup(ref)
        case Xor.Left(err) =>
          throw new SerializationException("Failed to deserialize JournalLookup: " + err.message)
      }
    }

    def write(command: JournalLookup, buf: BufferOutput[_ <: BufferOutput[_]], ser: Serializer) {
      writeBytes(buf, command.ref.toCborBytes)
    }
  }
  
  // JournalState serialization: serialization is required to be as fast as possible
  // because it dominates snapshot times, so we pardon all crimes and go straight to bytes
  // unfortunately it falls short and ends up being slower than POJO serialization.
  // only obvious way to make it faster is to avoid the copying from MultiHash.bytes
  // (requires change in scala-multihash)
  class JournalStateSerializer extends TypeSerializer[JournalState] {

    def write(state: JournalState, buf: BufferOutput[_ <: BufferOutput[_]], ser: Serializer) {
      writeBlock(buf, state)
      writeIndex(buf, state)
    }

    def read(klass: Class[JournalState], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalState = {
      val state = new JournalState
      readBlock(buf, state)
      readIndex(buf, state)
      state
    }
    
    // direct access implementation
    def write(buf: BufferOutput[_ <: BufferOutput[_]], state: JournalState) {
      writeBlock(buf, state)
      writeIndex(buf, state)
    }

    def read(buf: BufferInput[_ <: BufferInput[_]]): JournalState = {
      val state = new JournalState
      readBlock(buf, state)
      readIndex(buf, state)
      state
    }
    
    // implementation
    private def writeBlock(buf: BufferOutput[_ <: BufferOutput[_]], state: JournalState) {
      buf.writeLong(state.seqno.toLong) // it will be a cold day in hell if this overflows
      buf.writeInt(state.block.length)
      state.block.foreach(writeEntry(buf, _))
      writeOptRef(buf, state.blockchain)
    }

    private def readBlock(buf: BufferInput[_ <: BufferInput[_]], state: JournalState) {
      state.seqno = buf.readLong
      val blocklen = buf.readInt
      (1 to blocklen).foreach { _ =>
        state.block += readEntry(buf)
      }
      state.blockchain = readOptRef(buf)
    }
    
    private def writeIndex(buf: BufferOutput[_ <: BufferOutput[_]], state: JournalState) {
      buf.writeInt(state.index.size)
      state.index.foreach { 
        case (key, cref) =>
          writeRef(buf, key)
          writeChainRef(buf, cref)
      }
    }
    
    private def readIndex(buf: BufferInput[_ <: BufferInput[_]], state: JournalState) {
      val size = buf.readInt
      state.index.sizeHint(size)
      (1 to size).foreach { _ =>
        val key = readRef(buf)
        val cref = readChainRef(buf)
        state.index += (key -> cref)
      }
    }
        
    private def writeEntry(buf: BufferOutput[_ <: BufferOutput[_]], entry: JournalEntry) {
      entry match {
        case CanonicalEntry(index, ref) =>
          buf.writeByte(0)
          buf.writeLong(index.toLong)
          writeRef(buf, ref)
          
        case ChainEntry(index, ref, chain, chainPrevious) =>
          buf.writeByte(1)
          buf.writeLong(index.toLong)
          writeRef(buf, ref)
          writeRef(buf, chain)
          writeOptRef(buf, chainPrevious)
      }
    }
    
    private def readEntry(buf: BufferInput[_ <: BufferInput[_]]): JournalEntry = {
      buf.readByte match {
        case 0 =>
          val index = buf.readLong
          val ref = readRef(buf)
          CanonicalEntry(index, ref)
          
        case 1 =>
          val index = buf.readLong
          val ref = readRef(buf)
          val chain = readRef(buf)
          val chainPrevious = readOptRef(buf)
          ChainEntry(index, ref, chain, chainPrevious)

        case wtf => 
          throw new SerializationException("Failed to deserialize JournalEntry; bogus head " + wtf)
      }
    }
    
    private def writeOptRef(buf: BufferOutput[_ <: BufferOutput[_]], opt: Option[Reference]) {
      opt match {
        case None =>
          buf.writeByte(0)
          
        case Some(ref) =>
          buf.writeByte(1)
          writeRef(buf, ref)
      }
    }
    
    private def readOptRef(buf: BufferInput[_ <: BufferInput[_]]): Option[Reference] = {
      buf.readByte match {
        case 0 =>
          None
          
        case 1 =>
          val ref = readRef(buf)
          Some(ref)
          
        case wtf => 
          throw new SerializationException("Failed to deserialize Option[Reference]; bogus head " + wtf)
      }
    }

    private def writeRef(buf: BufferOutput[_ <: BufferOutput[_]], ref: Reference) {
      ref match {
        case MultihashReference(multihash) =>
          buf.writeByte(0)
          // MultiHash.bytes is inefficient; allocates new array every time it's called
          writeBytes(buf, multihash.bytes)
          
        case DummyReference(index) =>
          buf.writeByte(1)
          buf.writeInt(index)
      }
    }
    
    private def readRef(buf: BufferInput[_ <: BufferInput[_]]): Reference = {
      buf.readByte match {
        case 0 =>
          val bytes = readBytes(buf)
          MultiHash.fromBytes(bytes) match {
            case Xor.Right(ref) =>
              MultihashReference(ref)

            case Xor.Left(err) =>
              throw new SerializationException("Failed to deserialize Reference: " + err.toString)
          }
          
        case 1 =>
          val index = buf.readInt
          DummyReference(index)
          
        case wtf => 
          throw new SerializationException("Failed to deserialize Reference; bogus head " + wtf)
      }
    }
    
    private def writeChainRef(buf: BufferOutput[_ <: BufferOutput[_]], cref: ChainReference) {
      cref match {
        case EntityChainReference(chain) =>
          buf.writeByte(0)
          writeOptRef(buf, chain)
          
        case ArtefactChainReference(chain) =>
          buf.writeByte(1)
          writeOptRef(buf, chain)
      }
    }
    
    private def readChainRef(buf: BufferInput[_ <: BufferInput[_]]): ChainReference = {
      buf.readByte match {
        case 0 =>
          val chain = readOptRef(buf)
          EntityChainReference(chain)
          
        case 1 =>
          val chain = readOptRef(buf)
          ArtefactChainReference(chain)
          
        case wtf => 
          throw new SerializationException("Failed to deserialize ChainReference; bogus head " + wtf)
      }
    }
  }
}
