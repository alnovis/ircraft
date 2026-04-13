package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.Diagnostic

/**
  * Marker trait for constraints.
  *
  * {{{
  * trait MustBeResolved extends Constraint
  * trait MustNotBeEmpty extends Constraint
  * }}}
  */
trait Constraint

/**
  * Typeclass for verifying a constraint C on a value of type A.
  *
  * Returns empty Vector on success, or Vector of diagnostics on violation.
  *
  * {{{
  * implicit val resolved: ConstraintVerify[MustBeResolved, TypeExpr] =
  *   new ConstraintVerify[MustBeResolved, TypeExpr] {
  *     def verify(a: TypeExpr): Vector[Diagnostic] = ...
  *   }
  * }}}
  */
trait ConstraintVerify[C <: Constraint, A] {
  def verify(a: A): Vector[Diagnostic]
}

object ConstraintVerify {
  def apply[C <: Constraint, A](implicit ev: ConstraintVerify[C, A]): ConstraintVerify[C, A] = ev

  /** Create a ConstraintVerify from a function. */
  def instance[C <: Constraint, A](f: A => Vector[Diagnostic]): ConstraintVerify[C, A] =
    new ConstraintVerify[C, A] {
      def verify(a: A): Vector[Diagnostic] = f(a)
    }
}

/**
  * Wrapper that tags a value with a constraint.
  *
  * {{{
  * // Scala 2:
  * val x: Constrained[TypeExpr, MustBeResolved] = Constrained(TypeExpr.STR)
  *
  * // Scala 3 (with !> sugar):
  * val x: TypeExpr !> MustBeResolved = Constrained(TypeExpr.STR)
  * }}}
  */
final case class Constrained[A, C <: Constraint](value: A) {

  /** Verify the constraint on the wrapped value. */
  def verify(implicit cv: ConstraintVerify[C, A]): Vector[Diagnostic] =
    cv.verify(value)
}
