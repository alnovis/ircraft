package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.Diagnostic

/**
  * Marker trait for constraint types.
  *
  * Constraints are used with [[ConstraintVerify]] and [[Constrained]] to provide
  * compile-time-tagged, runtime-verified invariants on IR values. Define a new
  * constraint by extending this trait:
  *
  * {{{
  * trait MustBeResolved extends Constraint
  * trait MustNotBeEmpty extends Constraint
  * }}}
  *
  * @see [[ConstraintVerify]] for the verification typeclass
  * @see [[Constrained]] for value-level constraint tagging
  * @see [[ConstraintVerifier]] for generic tree-wide verification
  */
trait Constraint

/**
  * Typeclass for verifying a constraint `C` on a value of type `A`.
  *
  * Returns an empty `Vector` on success, or a `Vector` of [[Diagnostic]] messages
  * describing each violation found.
  *
  * {{{
  * implicit val resolved: ConstraintVerify[MustBeResolved, TypeExpr] =
  *   ConstraintVerify.instance { t =>
  *     t.foldMap {
  *       case TypeExpr.Unresolved(fqn) => Vector(Diagnostic(Severity.Error, s"Unresolved: $$fqn"))
  *       case _                        => Vector.empty
  *     }
  *   }
  * }}}
  *
  * @tparam C the constraint type (a subtype of [[Constraint]])
  * @tparam A the value type to verify
  * @see [[Constraint]] for defining new constraint types
  * @see [[Constrained]] for wrapping values with constraints
  */
trait ConstraintVerify[C <: Constraint, A] {

  /**
    * Verifies the constraint on the given value.
    *
    * @param a the value to verify
    * @return an empty `Vector` if the constraint is satisfied, or a `Vector` of
    *         [[Diagnostic]] messages for each violation
    */
  def verify(a: A): Vector[Diagnostic]
}

/**
  * Companion object for [[ConstraintVerify]], providing a summoner and factory method.
  */
object ConstraintVerify {

  /**
    * Summons the implicit [[ConstraintVerify]] instance for constraint `C` on type `A`.
    *
    * @tparam C the constraint type
    * @tparam A the value type
    * @param ev the implicit instance
    * @return the [[ConstraintVerify]] instance
    */
  def apply[C <: Constraint, A](implicit ev: ConstraintVerify[C, A]): ConstraintVerify[C, A] = ev

  /**
    * Creates a [[ConstraintVerify]] instance from a verification function.
    *
    * {{{
    * implicit val verify: ConstraintVerify[MustNotBeEmpty, String] =
    *   ConstraintVerify.instance { s =>
    *     if (s.trim.isEmpty) Vector(Diagnostic(Severity.Error, "Value must not be empty"))
    *     else Vector.empty
    *   }
    * }}}
    *
    * @tparam C the constraint type
    * @tparam A the value type
    * @param f the verification function returning diagnostics for violations
    * @return a new [[ConstraintVerify]] instance
    */
  def instance[C <: Constraint, A](f: A => Vector[Diagnostic]): ConstraintVerify[C, A] =
    new ConstraintVerify[C, A] {
      def verify(a: A): Vector[Diagnostic] = f(a)
    }
}

/**
  * A wrapper that tags a value with a constraint type at the type level.
  *
  * Use [[Constrained]] to track which constraints a value is expected to satisfy.
  * Call [[verify]] to check the constraint at runtime.
  *
  * {{{
  * // Scala 2:
  * val x: Constrained[TypeExpr, MustBeResolved] = Constrained(TypeExpr.STR)
  * val violations: Vector[Diagnostic] = x.verify
  *
  * // Scala 3 (with !> sugar, if defined):
  * val y: TypeExpr !> MustBeResolved = Constrained(TypeExpr.STR)
  * }}}
  *
  * @tparam A the value type
  * @tparam C the constraint type (a subtype of [[Constraint]])
  * @param value the wrapped value
  * @see [[ConstraintVerify]] for the verification typeclass invoked by [[verify]]
  */
final case class Constrained[A, C <: Constraint](value: A) {

  /**
    * Verifies the constraint on the wrapped value using the implicit
    * [[ConstraintVerify]] instance.
    *
    * @param cv the implicit constraint verification instance
    * @return an empty `Vector` if the constraint is satisfied, or a `Vector`
    *         of [[Diagnostic]] messages for each violation
    */
  def verify(implicit cv: ConstraintVerify[C, A]): Vector[Diagnostic] =
    cv.verify(value)
}
