package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import io.alnovis.ircraft.core.ir.TypeExpr

/** Constraint: TypeExpr must not contain any Unresolved references. */
trait MustBeResolved extends Constraint

object MustBeResolved {

  implicit val verifyTypeExpr: ConstraintVerify[MustBeResolved, TypeExpr] =
    ConstraintVerify.instance { t =>
      t.foldMap {
        case TypeExpr.Unresolved(fqn) => Vector(Diagnostic(Severity.Error, s"Unresolved type: $fqn"))
        case _                        => Vector.empty
      }
    }
}

/** Constraint: String must not be empty or whitespace-only. */
trait MustNotBeEmpty extends Constraint

object MustNotBeEmpty {

  implicit val verifyString: ConstraintVerify[MustNotBeEmpty, String] =
    ConstraintVerify.instance { s =>
      if (s.trim.isEmpty) Vector(Diagnostic(Severity.Error, "Value must not be empty"))
      else Vector.empty
    }
}
