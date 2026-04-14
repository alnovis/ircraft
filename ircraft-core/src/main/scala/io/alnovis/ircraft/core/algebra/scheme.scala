package io.alnovis.ircraft.core.algebra

import cats.{ Eval, Traverse }
import io.alnovis.ircraft.core.algebra.Algebra._

/**
  * Stack-safe recursion schemes over [[Fix]][F] structures.
  *
  * Provides the three fundamental recursion scheme operations:
  *  - [[cata]] (catamorphism): bottom-up fold
  *  - [[ana]] (anamorphism): top-down unfold
  *  - [[hylo]] (hylomorphism): unfold then fold without building an intermediate tree
  *
  * All operations are stack-safe via `cats.Eval` trampolining, making them suitable
  * for deeply nested IR trees.
  *
  * {{{
  * // Fold a Fix[SemanticF] tree to count declarations:
  * val countDecls: Fix[SemanticF] => Int = scheme.cata[SemanticF, Int] {
  *   case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => 1 + nested.sum
  *   case _ => 1
  * }
  * }}}
  *
  * @see [[Fix]] for the fixpoint type
  * @see [[Algebra]] for the algebra/coalgebra type aliases
  */
object scheme {

  /**
    * Catamorphism: fold a `Fix[F]` bottom-up using an [[Algebra.Algebra]].
    *
    * Recursively replaces each `F[Fix[F]]` layer with its algebra result,
    * starting from the leaves and working up to the root. Stack-safe via
    * `cats.Eval` trampolining.
    *
    * @tparam F the pattern functor (must have a `Traverse[F]` instance)
    * @tparam A the result (carrier) type
    * @param alg the algebra that collapses one level of `F[A]` into `A`
    * @return a function from `Fix[F]` to `A`
    */
  def cata[F[_], A](alg: Algebra[F, A])(implicit T: Traverse[F]): Fix[F] => A = {
    def go(fix: Fix[F]): Eval[A] = Eval.defer {
      T.traverse(fix.unfix)(child => go(child)).map(alg)
    }
    fix => go(fix).value
  }

  /**
    * Anamorphism: unfold a seed value into a `Fix[F]` using a [[Algebra.Coalgebra]].
    *
    * Recursively applies the coalgebra to produce `F` layers, starting from
    * the seed and building the tree top-down. Stack-safe via `cats.Eval`
    * trampolining.
    *
    * @tparam F the pattern functor (must have a `Traverse[F]` instance)
    * @tparam A the seed type
    * @param coalg the coalgebra that expands `A` into one level of `F[A]`
    * @return a function from `A` to `Fix[F]`
    */
  def ana[F[_], A](coalg: Coalgebra[F, A])(implicit T: Traverse[F]): A => Fix[F] = {
    def go(a: A): Eval[Fix[F]] = Eval.defer {
      T.traverse(coalg(a))(child => go(child)).map(Fix(_))
    }
    a => go(a).value
  }

  /**
    * Hylomorphism: unfold a seed into `F` layers, then fold them, without
    * materializing the intermediate `Fix[F]` tree.
    *
    * Equivalent to `cata(alg) compose ana(coalg)` but more efficient because
    * no intermediate tree is allocated. Stack-safe via `cats.Eval` trampolining.
    *
    * @tparam F the pattern functor (must have a `Traverse[F]` instance)
    * @tparam A the seed type (input to the coalgebra)
    * @tparam B the result type (output of the algebra)
    * @param alg   the algebra that folds `F[B]` into `B`
    * @param coalg the coalgebra that unfolds `A` into `F[A]`
    * @return a function from `A` to `B`
    */
  def hylo[F[_], A, B](alg: Algebra[F, B], coalg: Coalgebra[F, A])(implicit T: Traverse[F]): A => B = {
    def go(a: A): Eval[B] = Eval.defer {
      T.traverse(coalg(a))(child => go(child)).map(alg)
    }
    a => go(a).value
  }
}
