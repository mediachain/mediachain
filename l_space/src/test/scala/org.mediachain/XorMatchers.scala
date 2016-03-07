package org.mediachain

import org.specs2.matcher.Matcher
import org.specs2.execute.{Failure, Result}
import org.specs2.matcher.MatchersImplicits._
import cats.data.Xor

trait XorMatchers {
  def beRightXor[T](checker: (T => Result)): Matcher[Xor[_, T]] = {
    (x: Xor[_, T]) => x match {
      case Xor.Right(value) =>
        checker(value)
      case _ =>
        new Failure(s"Value $x was instance of Xor.Left")
    }
  }

  def beRightXor(): Matcher[Xor[_, _]] = { (x: Xor[_, _]) =>
    (x.isRight, "Xor value was instance of Xor.Left")
  }

  def beLeftXor[T](checker: (T => Result)): Matcher[Xor[T, _]] = {
    (x: Xor[T, _]) => x match {
      case Xor.Left(value) =>
        checker(value)
      case _ =>
        new Failure(s"Value $x was instance of Xor.Right")
    }
  }

  def beLeftXor(): Matcher[Xor[_, _]] = { (x: Xor[_, _]) =>
    (x.isLeft, "Xor value was instance of Xor.Right")
  }
}
