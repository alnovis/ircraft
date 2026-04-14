package io.alnovis.ircraft.core.algebra

import cats.{ Eval, Traverse }
import io.alnovis.ircraft.core.algebra.Algebra._

/** Stack-safe recursion schemes over Fix[F]. */
object scheme {

  /**
    * Catamorphism: fold a Fix[F] bottom-up using an algebra.
    * Stack-safe via cats.Eval trampolining.
    */
  def cata[F[_], A](alg: Algebra[F, A])(implicit T: Traverse[F]): Fix[F] => A = {
    def go(fix: Fix[F]): Eval[A] = Eval.defer {
      T.traverse(fix.unfix)(child => go(child)).map(alg)
    }
    fix => go(fix).value
  }

  /**
    * Anamorphism: unfold a value into Fix[F] using a coalgebra.
    * Stack-safe via cats.Eval trampolining.
    */
  def ana[F[_], A](coalg: Coalgebra[F, A])(implicit T: Traverse[F]): A => Fix[F] = {
    def go(a: A): Eval[Fix[F]] = Eval.defer {
      T.traverse(coalg(a))(child => go(child)).map(Fix(_))
    }
    a => go(a).value
  }

  /** Hylomorphism: unfold then fold without building the intermediate tree. */
  def hylo[F[_], A, B](alg: Algebra[F, B], coalg: Coalgebra[F, A])(implicit T: Traverse[F]): A => B = {
    def go(a: A): Eval[B] = Eval.defer {
      T.traverse(coalg(a))(child => go(child)).map(alg)
    }
    a => go(a).value
  }
}
