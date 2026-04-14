package io.alnovis.ircraft.core.algebra

/**
  * Scala 3 infix type syntax for [[Constrained]].
  *
  * Provides the `!>` operator as a more readable alternative to `Constrained[A, C]`.
  * This is only available on Scala 3; Scala 2 users must write `Constrained[A, C]` directly.
  *
  * {{{
  * // Instead of:
  * val x: Constrained[TypeExpr, MustBeResolved] = Constrained(TypeExpr.STR)
  *
  * // Write:
  * val x: TypeExpr !> MustBeResolved = Constrained(TypeExpr.STR)
  * }}}
  *
  * @tparam A the value type being constrained
  * @tparam C the [[Constraint]] marker trait
  * @see [[Constrained]] for the wrapper type
  * @see [[Constraint]] for the marker trait
  * @see [[ConstraintVerify]] for the verification typeclass
  */
infix type !>[A, C <: Constraint] = Constrained[A, C]
