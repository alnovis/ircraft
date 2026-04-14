package io.alnovis.ircraft.core.algebra

/**
  * Fixpoint of a higher-kinded functor `F`. Ties the recursive knot so that
  * `Fix[F]` is isomorphic to `F[Fix[F]]`.
  *
  * This is the standard encoding for recursion schemes: instead of defining
  * recursive data types directly, the recursion is factored out into a functor `F`
  * and reintroduced via [[Fix]]. This allows generic folding ([[scheme.cata]]),
  * unfolding ([[scheme.ana]]), and refactoring ([[scheme.hylo]]) operations.
  *
  * {{{
  * // Given a functor:
  * //   sealed trait ExprF[+A]
  * //   case class LitF(value: Int) extends ExprF[Nothing]
  * //   case class AddF[A](left: A, right: A) extends ExprF[A]
  *
  * // Build a tree:
  * val tree: Fix[ExprF] = Fix(AddF(Fix(LitF(1)), Fix(LitF(2))))
  *
  * // Fold it:
  * val result: Int = scheme.cata[ExprF, Int] {
  *   case LitF(v)    => v
  *   case AddF(l, r) => l + r
  * }.apply(tree)
  * // result == 3
  * }}}
  *
  * @tparam F the pattern functor (must have a `Traverse[F]` instance for recursion schemes)
  * @param unfix the one-level unrolling of the recursive structure
  * @see [[scheme]] for catamorphism, anamorphism, and hylomorphism over `Fix[F]`
  * @see [[Algebra.Algebra]] for the type alias used by recursion scheme folds
  * @see [[Algebra.Coalgebra]] for the type alias used by recursion scheme unfolds
  */
final case class Fix[F[_]](unfix: F[Fix[F]])
