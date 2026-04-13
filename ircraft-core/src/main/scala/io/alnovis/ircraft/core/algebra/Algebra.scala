package io.alnovis.ircraft.core.algebra

object Algebra {

  /** F-algebra: one-level fold. */
  type Algebra[F[_], A] = F[A] => A

  /** F-coalgebra: one-level unfold. */
  type Coalgebra[F[_], A] = A => F[A]

  /** Monadic F-algebra. */
  type AlgebraM[F[_], M[_], A] = F[A] => M[A]
}
