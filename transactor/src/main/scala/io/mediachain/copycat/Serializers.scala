package io.mediachain.copycat

import io.atomix.catalyst.serializer.{Serializer, TypeSerializer, SerializationException}
import io.atomix.catalyst.buffer.{BufferInput, BufferOutput}
import cats.data.Xor
import io.mediachain.protocol.Datastore.JournalBlock
import io.mediachain.protocol.CborSerialization
import io.mediachain.copycat.StateMachine._

object Serializers {
  val klasses = List(classOf[JournalInsert],
                     classOf[JournalUpdate],
                     classOf[JournalLookup],
                     classOf[JournalCurrentBlock],
                     classOf[JournalCommitEvent],
                     classOf[JournalBlockEvent],
                     classOf[JournalState])
  
  def register(serializer: Serializer) {
    klasses.foreach(serializer.register(_))
    serializer.register(classOf[JournalBlock], classOf[JournalBlockSerializer])
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

}
