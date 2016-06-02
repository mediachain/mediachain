package io.mediachain.copycat

import io.atomix.catalyst.serializer.{SerializationException, Serializer, TypeSerializer}
import io.atomix.catalyst.buffer.{BufferInput, BufferOutput}
import cats.data.Xor
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.CborSerialization
import io.mediachain.copycat.StateMachine._


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
      CborSerialization.dataObjectFromCborBytes(bytes) match {
        case Xor.Right(block: JournalBlock) => 
          block
        case Xor.Right(obj) =>
          throw new SerializationException("Failed to deserialize JournalBlock: unexpected object " + obj)
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
      CborSerialization.dataObjectFromCborBytes(bytes) match {
        case Xor.Right(record: CanonicalRecord) =>
          JournalInsert(record)
        case Xor.Right(obj) =>
          throw new SerializationException("Failed to deserialize JournalInsert: unexpected object " + obj)
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
}
