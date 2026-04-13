package io.alnovis.ircraft.core.algebra

/** Fixpoint of a functor F. Ties the recursive knot: Fix[F] = F[Fix[F]]. */
final case class Fix[F[_]](unfix: F[Fix[F]])
