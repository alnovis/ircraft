package io.alnovis.ircraft.core.algebra

/**
  * Scala 3 infix type syntax for Constrained.
  *
  * {{{
  * val x: TypeExpr !> MustBeResolved = Constrained(TypeExpr.STR)
  * }}}
  */
infix type !>[A, C <: Constraint] = Constrained[A, C]
