package io.alnovis.ircraft.core.algebra

import cats.{Applicative, Eval, Functor, Traverse}

/** Coproduct (disjoint union) of two functors. */
sealed trait Coproduct[F[_], G[_], A]

object Coproduct {
  final case class Inl[F[_], G[_], A](fa: F[A]) extends Coproduct[F, G, A]
  final case class Inr[F[_], G[_], A](ga: G[A]) extends Coproduct[F, G, A]

  // kind-projector: Coproduct[F, G, *] is the partially-applied type
  // Users can define:  type :+:[F[_], G[_]] = Coproduct[F, G, *]

  implicit def coproductFunctor[F[_], G[_]](implicit F: Functor[F], G: Functor[G]): Functor[Coproduct[F, G, *]] =
    new Functor[Coproduct[F, G, *]] {
      def map[A, B](fa: Coproduct[F, G, A])(f: A => B): Coproduct[F, G, B] = fa match {
        case Inl(a) => Inl(F.map(a)(f))
        case Inr(a) => Inr(G.map(a)(f))
      }
    }

  implicit def coproductTraverse[F[_], G[_]](implicit F: Traverse[F], G: Traverse[G]): Traverse[Coproduct[F, G, *]] =
    new Traverse[Coproduct[F, G, *]] {
      def traverse[H[_], A, B](fa: Coproduct[F, G, A])(f: A => H[B])(implicit H: Applicative[H]): H[Coproduct[F, G, B]] = fa match {
        case Inl(a) => H.map(F.traverse(a)(f))(Inl(_): Coproduct[F, G, B])
        case Inr(a) => H.map(G.traverse(a)(f))(Inr(_): Coproduct[F, G, B])
      }

      def foldLeft[A, B](fa: Coproduct[F, G, A], b: B)(f: (B, A) => B): B = fa match {
        case Inl(a) => F.foldLeft(a, b)(f)
        case Inr(a) => G.foldLeft(a, b)(f)
      }

      def foldRight[A, B](fa: Coproduct[F, G, A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
        case Inl(a) => F.foldRight(a, lb)(f)
        case Inr(a) => G.foldRight(a, lb)(f)
      }
    }

  implicit def coproductDialectInfo[F[_], G[_]](implicit F: DialectInfo[F], G: DialectInfo[G]): DialectInfo[Coproduct[F, G, *]] =
    DialectInfo(s"${F.dialectName} :+: ${G.dialectName}", F.operationCount + G.operationCount)
}
