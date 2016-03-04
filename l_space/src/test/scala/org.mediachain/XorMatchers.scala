package org.mediachain

import org.specs2.matcher.Matcher
import org.specs2.matcher.MatchersImplicits._
import cats.data.Xor

trait XorMatchers {
  def beRight[T](validator: (T => Boolean)): Matcher[Xor[_, T]] = {
    (x: Xor[_, T]) => x match {
      case container@Xor.Right(value) =>
        (validator(value), s"$container did not match validator")
      case _ =>
        (false, "Xor value was instance of Xor.Left")
    }
  }

  def beRight[T](value: T): Matcher[Xor[_, T]] = beRight {
    (x: T) => x == value
  }

  def beRight(): Matcher[Xor[_, _]] = { (x: Xor[_, _]) =>
    (x.isRight, "Xor value was instance of Xor.Left")
  }

  def beLeft[T](validator: (T => Boolean)): Matcher[Xor[T, _]] = {
    (x: Xor[T, _]) => x match {
      case container@Xor.Left(value) =>
        (validator(value), s"$container did not match validator")
      case _ =>
        (false, "Xor value was instance of Xor.Right")
    }
  }

  def beLeft[T](value: T): Matcher[Xor[T, _]] = beLeft {
    (x: T) => x == value
  }

  def beLeft(): Matcher[Xor[_, _]] = { (x: Xor[_, _]) =>
    (x.isLeft, "Xor value was instance of Xor.Right")
  }
}
