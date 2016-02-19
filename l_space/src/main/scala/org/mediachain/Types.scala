package org.mediachain

object Types {
  type Canonical = String

  case class Person(id: String,
                    name: String)

  case class PhotoBlob(id: String,
                       title: String,
                       description: String,
                       date: String,
                       author: Option[Person])
}
