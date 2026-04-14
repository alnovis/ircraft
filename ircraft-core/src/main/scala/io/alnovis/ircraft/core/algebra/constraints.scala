package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import io.alnovis.ircraft.core.ir.TypeExpr

/**
  * Constraint: all [[TypeExpr]] references must be resolved (no [[TypeExpr.Unresolved]] nodes).
  *
  * This constraint is typically verified after a type resolution pass has run.
  * An [[TypeExpr.Unresolved]] node anywhere in the type expression tree will produce
  * an error [[Diagnostic]].
  *
  * {{{
  * val check = Constrained[TypeExpr, MustBeResolved](myType)
  * val errors: Vector[Diagnostic] = check.verify
  * }}}
  *
  * @see [[ConstraintVerify]] for the verification mechanism
  * @see [[ConstraintVerifier.verifyFieldTypes]] for tree-wide field type verification
  */
trait MustBeResolved extends Constraint

/**
  * Companion providing the implicit [[ConstraintVerify]] instance for
  * [[MustBeResolved]] on [[TypeExpr]].
  */
object MustBeResolved {

  /**
    * Verifies that a [[TypeExpr]] contains no [[TypeExpr.Unresolved]] references.
    *
    * Recursively traverses the type expression tree using `foldMap` and produces
    * an error diagnostic for each unresolved reference found.
    */
  implicit val verifyTypeExpr: ConstraintVerify[MustBeResolved, TypeExpr] =
    ConstraintVerify.instance { t =>
      t.foldMap {
        case TypeExpr.Unresolved(fqn) => Vector(Diagnostic(Severity.Error, s"Unresolved type: $fqn"))
        case _                        => Vector.empty
      }
    }
}

/**
  * Constraint: a `String` value must not be empty or whitespace-only.
  *
  * Typically used to verify that declaration names are non-empty.
  *
  * {{{
  * val check = Constrained[String, MustNotBeEmpty](name)
  * val errors: Vector[Diagnostic] = check.verify
  * }}}
  *
  * @see [[ConstraintVerify]] for the verification mechanism
  * @see [[ConstraintVerifier.verifyNames]] for tree-wide name verification
  */
trait MustNotBeEmpty extends Constraint

/**
  * Companion providing the implicit [[ConstraintVerify]] instance for
  * [[MustNotBeEmpty]] on `String`.
  */
object MustNotBeEmpty {

  /**
    * Verifies that a `String` is not empty or whitespace-only.
    *
    * Produces a single error diagnostic if the string (after trimming) is empty.
    */
  implicit val verifyString: ConstraintVerify[MustNotBeEmpty, String] =
    ConstraintVerify.instance { s =>
      if (s.trim.isEmpty) Vector(Diagnostic(Severity.Error, "Value must not be empty"))
      else Vector.empty
    }
}
