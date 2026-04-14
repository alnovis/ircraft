package io.alnovis.ircraft.core.algebra

import cats.{Applicative, Eval, Traverse}

/**
  * A coproduct (disjoint union) of two higher-kinded functors.
  *
  * Coproduct is the fundamental building block for composing dialect functors
  * in the "Data Types a la Carte" style. A value of `Coproduct[F, G, A]` is
  * either an `F[A]` (wrapped in [[Coproduct.Inl]]) or a `G[A]` (wrapped in
  * [[Coproduct.Inr]]).
  *
  * Using the kind-projector plugin, the partially-applied type
  * `Coproduct[F, G, *]` can be used directly as a functor. Users may also
  * define a type alias for syntactic convenience:
  * {{{
  * type :+:[F[_], G[_]] = Coproduct[F, G, *]
  * }}}
  *
  * @tparam F the left functor
  * @tparam G the right functor
  * @tparam A the carrier type
  * @see [[Inject]] for automatic injection into coproducts
  * @see [[io.alnovis.ircraft.core.algebra.eliminate]] for coproduct shrinking
  */
sealed trait Coproduct[F[_], G[_], A]

/**
  * Companion object for [[Coproduct]] providing case class constructors
  * and implicit typeclass instances.
  */
object Coproduct {

  /**
    * Left injection: wraps an `F[A]` value as a [[Coproduct]].
    *
    * @param fa the left functor value
    * @tparam F the left functor
    * @tparam G the right functor
    * @tparam A the carrier type
    */
  final case class Inl[F[_], G[_], A](fa: F[A]) extends Coproduct[F, G, A]

  /**
    * Right injection: wraps a `G[A]` value as a [[Coproduct]].
    *
    * @param ga the right functor value
    * @tparam F the left functor
    * @tparam G the right functor
    * @tparam A the carrier type
    */
  final case class Inr[F[_], G[_], A](ga: G[A]) extends Coproduct[F, G, A]

  // kind-projector: Coproduct[F, G, *] is the partially-applied type
  // Users can define:  type :+:[F[_], G[_]] = Coproduct[F, G, *]

  /**
    * Implicit [[cats.Traverse]] instance for `Coproduct[F, G, *]`.
    *
    * Delegates traversal to the [[cats.Traverse]] instance of whichever
    * side ([[Inl]] or [[Inr]]) the coproduct inhabits.
    *
    * @param F the [[cats.Traverse]] instance for the left functor
    * @param G the [[cats.Traverse]] instance for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[cats.Traverse]] for the coproduct `Coproduct[F, G, *]`
    */
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

  /**
    * Implicit [[DialectInfo]] instance for `Coproduct[F, G, *]`.
    *
    * Composes the dialect names with " :+: " separator and sums
    * the operation counts of both constituent dialects.
    *
    * @param F the [[DialectInfo]] for the left functor
    * @param G the [[DialectInfo]] for the right functor
    * @tparam F the left functor
    * @tparam G the right functor
    * @return a [[DialectInfo]] for the composed coproduct
    */
  implicit def coproductDialectInfo[F[_], G[_]](implicit F: DialectInfo[F], G: DialectInfo[G]): DialectInfo[Coproduct[F, G, *]] =
    DialectInfo(s"${F.dialectName} :+: ${G.dialectName}", F.operationCount + G.operationCount)
}
