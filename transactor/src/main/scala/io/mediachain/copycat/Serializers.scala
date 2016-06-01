package io.mediachain.copycat

import io.atomix.catalyst.serializer.{SerializationException, Serializer, TypeSerializer}
import io.atomix.catalyst.buffer.{BufferInput, BufferOutput}
import cats.data.Xor
import io.mediachain.protocol.Datastore._
import io.mediachain.protocol.CborSerialization
import io.mediachain.copycat.StateMachine._

object Serializers {
  val klasses = List(classOf[JournalLookup],
                     classOf[JournalCurrentBlock],
                     classOf[JournalCommitEvent],
                     classOf[JournalBlockEvent],
                     classOf[JournalState])
  
  def register(serializer: Serializer) {
    klasses.foreach(serializer.register(_))
    serializer.register(classOf[JournalBlock], classOf[JournalBlockSerializer])
    serializer.register(classOf[JournalInsert], classOf[JournalInsertSerializer])
    serializer.register(classOf[JournalUpdate], classOf[JournalUpdateSerializer])
  }
  
  class JournalBlockSerializer extends TypeSerializer[JournalBlock] {
    def read(klass: Class[JournalBlock], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalBlock = {
      val len = buf.readInt()
      val bytes = new Array[Byte](len)
      buf.read(bytes)
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
      val bytes = block.toCborBytes
      buf.writeInt(bytes.length)
      buf.write(bytes)
    }
  }

  class JournalInsertSerializer extends TypeSerializer[JournalInsert] {
    def read(klass: Class[JournalInsert], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalInsert = {
      val len = buf.readInt()
      val bytes = new Array[Byte](len)
      buf.read(bytes)
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
      val bytes = command.record.toCborBytes
      buf.writeInt(bytes.length)
      buf.write(bytes)
    }
  }

  class JournalUpdateSerializer extends TypeSerializer[JournalUpdate] {
    def read(klass: Class[JournalUpdate], buf: BufferInput[_ <: BufferInput[_]], ser: Serializer)
    : JournalUpdate = {
      val refLen = buf.readInt()
      val refBytes = new Array[Byte](refLen)
      buf.read(refBytes)

      val cellLen = buf.readInt()
      val cellBytes = new Array[Byte](cellLen)
      buf.read(cellBytes)

      val updateXor = for {
        ref <- CborSerialization.fromCborBytes[Reference](refBytes)
        cell <- CborSerialization.fromCborBytes[ChainCell](cellBytes)
      } yield JournalUpdate(ref, cell)

      updateXor match {
        case Xor.Left(err) =>
          throw new SerializationException(
            s"Failed to deserialize JournalUpdate: ${err.message}"
          )
        case Xor.Right(obj) => obj
      }
    }

    def write(update: JournalUpdate, buf: BufferOutput[_ <: BufferOutput[_]], ser: Serializer) {
      val refBytes = update.ref.toCborBytes
      val cellBytes = update.cell.toCborBytes
      buf.writeInt(refBytes.length)
      buf.write(refBytes)
      buf.writeInt(cellBytes.length)
      buf.write(cellBytes)
    }
  }

}
