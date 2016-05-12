package io.mediachain.util.gen

object GenInstances {
  import cats.Applicative
  import org.scalacheck.Gen

  implicit def genIsApplicative: Applicative[Gen] = new Applicative[Gen] {
    override def pure[A](x: A): Gen[A] = Gen.const(x)

    override def ap[A, B](ff: Gen[(A) => B])(fa: Gen[A]): Gen[B] =
      fa.flatMap(x => ff.map(f => f(x)))

    override def map[A, B](fa: Gen[A])(f: (A) => B): Gen[B] = fa.map(f)

    override def product[A, B](fa: Gen[A], fb: Gen[B]): Gen[(A, B)] =
      fa.flatMap { a =>
        fb.flatMap { b =>
          pure((a, b))
        }
      }
  }
}
