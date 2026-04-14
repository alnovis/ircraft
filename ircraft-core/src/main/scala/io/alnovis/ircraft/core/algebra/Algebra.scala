package io.alnovis.ircraft.core.algebra

/**
  * Type aliases for F-algebras, F-coalgebras, and monadic F-algebras.
  *
  * These are the fundamental building blocks for recursion schemes over
  * [[Fix]]-wrapped data structures.
  *
  * @see [[scheme]] for functions that consume these algebras (cata, ana, hylo)
  * @see [[Fix]] for the fixpoint type they operate on
  */
object Algebra {

  /**
    * An F-algebra: a function that collapses one level of a functor `F`
    * into a result type `A`.
    *
    * Used by [[scheme.cata]] to fold a `Fix[F]` bottom-up.
    *
    * {{{
    * val eval: Algebra[ExprF, Int] = {
    *   case LitF(v)    => v
    *   case AddF(l, r) => l + r
    * }
    * }}}
    *
    * @tparam F the pattern functor
    * @tparam A the result (carrier) type
    */
  type Algebra[F[_], A] = F[A] => A

  /**
    * An F-coalgebra: a function that unfolds a seed value `A` into one level
    * of a functor `F`.
    *
    * Used by [[scheme.ana]] to build a `Fix[F]` top-down from a seed.
    *
    * {{{
    * val generate: Coalgebra[ListF, Int] = {
    *   case 0 => NilF
    *   case n => ConsF(n, n - 1)
    * }
    * }}}
    *
    * @tparam F the pattern functor
    * @tparam A the seed type
    */
  type Coalgebra[F[_], A] = A => F[A]

  /**
    * A monadic F-algebra: an F-algebra whose result is wrapped in an effect `M`.
    *
    * Enables effectful folding where each step may perform side effects.
    *
    * @tparam F the pattern functor
    * @tparam M the effect type
    * @tparam A the result (carrier) type
    */
  type AlgebraM[F[_], M[_], A] = F[A] => M[A]
}
