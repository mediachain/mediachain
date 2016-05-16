package io.mediachain.copycat

import io.atomix.catalyst.serializer.Serializer
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
  }
}
