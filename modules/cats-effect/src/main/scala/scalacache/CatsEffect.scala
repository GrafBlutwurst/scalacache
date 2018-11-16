package scalacache

import cats.Functor
import cats.evidence.<~<
import cats.effect.{Async => CatsAsync, IO, Resource}

import scala.language.higherKinds
import scala.util.control.NonFatal

object CatsEffect {

  trait LowPrioModes {

    /**
      * A mode that wraps computations in F[_],
      * where there is an instance of cats-effect Async available for F.
      */
    implicit def async[F[_]](implicit F: CatsAsync[F]): Mode[F] = new Mode[F] {
      val M: Async[F] = asyncForCatsEffectAsync[F]
    }
  }

  object modes extends LowPrioModes {

    /**
      * A mode that wraps computations in cats-effect IO.
      */
    implicit val io: Mode[IO] = new Mode[IO] {
      val M: Async[IO] = asyncForCatsEffectAsync[IO]
    }

  }

  def asyncForCatsEffectAsync[F[_]](implicit af: CatsAsync[F]): Async[F] = new Async[F] {

    def pure[A](a: A): F[A] = af.pure(a)

    def flatMap[A, B](fa: F[A])(f: (A) => F[B]): F[B] = af.flatMap(fa)(f)

    def map[A, B](fa: F[A])(f: (A) => B): F[B] = af.map(fa)(f)

    def raiseError[A](t: Throwable): F[A] = af.raiseError(t)

    def handleNonFatal[A](fa: => F[A])(f: Throwable => A): F[A] = af.recover(fa) {
      case NonFatal(e) => f(e)
    }

    def delay[A](thunk: => A): F[A] = af.delay(thunk)

    def suspend[A](thunk: => F[A]): F[A] = af.suspend(thunk)

    def async[A](register: (Either[Throwable, A] => Unit) => Unit): F[A] = af.async(register)

  }

  def resourceCache[F[_]: CatsAsync: Functor: Mode, G[_], V](cacheF: F[G[V]])(
      implicit ev: G[V] <~< Cache[V]): Resource[F, G[V]] =
    Resource.make(cacheF)(toClose => Functor[F].map(ev(toClose).close())(_ => ()))

}
