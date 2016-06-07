package io.mediachain.copycat

import io.atomix.catalyst.buffer.DirectBuffer
import io.atomix.catalyst.serializer.{Serializer, TypeSerializer}
import io.mediachain.BaseSpec
import io.mediachain.multihash.MultiHash

import scala.util.Random

object CopycatSerializerSpec extends BaseSpec {
  import Serializers._
  import io.mediachain.copycat.StateMachine._
  import io.mediachain.protocol.Datastore._
  import io.mediachain.util.cbor.CborAST._


  def is =
    s2"""
       round-trip serializes:
         - JournalInsert $journalInsertRoundTrip
         - JournalUpdate $journalUpdateRoundTrip
         - JournalLookup $journalLookupRoundTrip
      """

  private def roundTrip[T](value: T, typeSerializer: TypeSerializer[T]) = {
    val buffer = DirectBuffer.allocate()
    val serializer = new Serializer()
    serializer.register(value.getClass, typeSerializer.getClass)
    typeSerializer.write(value, buffer, serializer)
    buffer.position(0)
    val deserialized = typeSerializer.read(value.getClass.asInstanceOf[Class[T]], buffer, serializer)
    deserialized must_== value
  }

  private def randomRef: Reference =
    MultihashReference(MultiHash.hashWithSHA256(Random.nextString(100).getBytes))

  def journalInsertRoundTrip = {
    val meta = Map("foo" -> CMap(CString("bar") -> CString("baz")))
    val insert = JournalInsert(Entity(meta))

    roundTrip(insert, new JournalInsertSerializer)
  }

  def journalUpdateRoundTrip = {
    val meta = Map("foo" -> CMap(CString("bar") -> CString("baz")))
    val metaSource = Some(randomRef)
    val ref = randomRef
    val update = JournalUpdate(ref, EntityUpdateCell(ref, None, meta, metaSource))

    roundTrip(update, new JournalUpdateSerializer)
  }

  def journalLookupRoundTrip = {
    val ref = randomRef
    val lookup = JournalLookup(ref)
    roundTrip(lookup, new JournalLookupSerializer)
  }
}
