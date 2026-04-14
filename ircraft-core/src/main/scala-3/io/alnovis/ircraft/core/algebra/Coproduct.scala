package io.alnovis.ircraft.core.algebra

import cats.{ Applicative, Eval, Functor, Traverse }
import io.alnovis.ircraft.core.algebra.DialectInfo
import cats.syntax.all._

/**
  * Coproduct (disjoint union) of two higher-kinded functors.
  *
  * This is the core mechanism for dialect composition in ircraft, implementing
  * the "Data Types a la Carte" approach. A `Coproduct[F, G, A]` value is either
  * an `F[A]` (wrapped in [[Coproduct.Inl]]) or a `G[A]` (wrapped in [[Coproduct.Inr]]).
  *
  * Dialect composition is right-associative via the [[`:+:`]] type alias:
  * {{{
  * type MyIR = ProtoF :+: GraphQLF :+: SemanticF
  * // expands to: Coproduct[ProtoF, Coproduct[GraphQLF, SemanticF, _], _]
  * }}}
  *
  * @tparam F the left functor (typically a dialect extension)
  * @tparam G the right functor (typically [[io.alnovis.ircraft.core.ir.SemanticF]] or another coproduct)
  * @tparam A the recursive position type
  * @see [[`:+:`]] for the infix type alias
  * @see [[Inject]] for type-safe injection into coproducts
  * @see [[eliminate]] for dialect elimination (lowering)
  */
enum Coproduct[F[_], G[_], A]:
  /** Left injection: wraps an `F[A]` value. */
  case Inl(fa: F[A])

  /** Right injection: wraps a `G[A]` value. */
  case Inr(ga: G[A])

/**
  * Right-associative infix type alias for [[Coproduct]].
  *
  * Enables the syntax `F :+: G` instead of `Coproduct[F, G, _]`.
  * Because Scala's type lambda `[A] =>> Coproduct[F, G, A]` is used,
  * this alias produces a proper higher-kinded type suitable for
  * `Functor`, `Traverse`, etc.
  *
  * {{{
  * type MyIR = ProtoF :+: SemanticF
  * val node: Fix[MyIR] = ...
  * }}}
  *
  * @tparam F the left functor
  * @tparam G the right functor
  */
type :+:[F[_], G[_]] = [A] =>> Coproduct[F, G, A]

/**
  * Companion object for [[Coproduct]] providing typeclass instances.
  */
object Coproduct:

  /**
    * `cats.Traverse` instance for `F :+: G`, derived from the `Traverse` instances of `F` and `G`.
    *
    * Since `Traverse` extends `Functor`, this also provides `Functor[F :+: G]`.
    *
    * @tparam F the left functor (must have a `Traverse` instance)
    * @tparam G the right functor (must have a `Traverse` instance)
    * @return a `Traverse` instance for the coproduct
    */
  given coproductTraverse[F[_], G[_]](using F: Traverse[F], G: Traverse[G]): Traverse[F :+: G] with

    /**
      * Traverses the value inside the coproduct with an effectful function.
      *
      * @tparam H the applicative effect type
      * @tparam A the input recursive position type
      * @tparam B the output recursive position type
      * @param fa the coproduct value to traverse
      * @param f  the effectful transformation
      * @return the traversed coproduct wrapped in `H`
      */
    def traverse[H[_]: Applicative, A, B](fa: (F :+: G)[A])(f: A => H[B]): H[(F :+: G)[B]] = fa match
      case Coproduct.Inl(a) => F.traverse(a)(f).map(Coproduct.Inl(_))
      case Coproduct.Inr(a) => G.traverse(a)(f).map(Coproduct.Inr(_))

    /**
      * Left-associative fold over the recursive children in the coproduct.
      *
      * @tparam A the recursive child type
      * @tparam B the accumulator type
      * @param fa the coproduct value to fold
      * @param b  the initial accumulator
      * @param f  the combining function
      * @return the final accumulated value
      */
    def foldLeft[A, B](fa: (F :+: G)[A], b: B)(f: (B, A) => B): B = fa match
      case Coproduct.Inl(a) => F.foldLeft(a, b)(f)
      case Coproduct.Inr(a) => G.foldLeft(a, b)(f)

    /**
      * Right-associative lazy fold over the recursive children in the coproduct.
      *
      * @tparam A the recursive child type
      * @tparam B the result type
      * @param fa the coproduct value to fold
      * @param lb the lazy initial value
      * @param f  the combining function
      * @return the lazily computed result
      */
    def foldRight[A, B](fa: (F :+: G)[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case Coproduct.Inl(a) => F.foldRight(a, lb)(f)
      case Coproduct.Inr(a) => G.foldRight(a, lb)(f)

  /**
    * [[DialectInfo]] instance for a coproduct, combining the names and operation counts of both components.
    *
    * @tparam F the left functor (must have a [[DialectInfo]] instance)
    * @tparam G the right functor (must have a [[DialectInfo]] instance)
    * @return a [[DialectInfo]] with combined name and summed operation count
    */
  given coproductDialectInfo[F[_], G[_]](using F: DialectInfo[F], G: DialectInfo[G]): DialectInfo[F :+: G] =
    DialectInfo(s"${F.dialectName} :+: ${G.dialectName}", F.operationCount + G.operationCount)
