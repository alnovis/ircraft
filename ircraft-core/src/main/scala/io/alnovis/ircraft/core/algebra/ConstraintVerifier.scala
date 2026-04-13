package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.Diagnostic
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir.{ Field, TypeExpr }

/** Generic constraint verification over Fix[F] trees via scheme.cata. */
object ConstraintVerifier {

  /**
    * Verify that all field types in the tree satisfy a constraint.
    * Requires HasFields to extract fields from each node.
    */
  def verifyFieldTypes[F[_], C <: Constraint](
    implicit T: Traverse[F],
    HF: HasFields[F],
    CV: ConstraintVerify[C, TypeExpr]
  ): Fix[F] => Vector[Diagnostic] =
    scheme.cata[F, Vector[Diagnostic]] { fa =>
      val fieldDiags = HF.fields(fa).flatMap(f => CV.verify(f.fieldType))
      val childDiags = T.foldLeft(fa, Vector.empty[Diagnostic])(_ ++ _)
      fieldDiags ++ childDiags
    }

  /**
    * Verify that all names in the tree satisfy a constraint.
    * Requires HasName to extract name from each node.
    */
  def verifyNames[F[_], C <: Constraint](
    implicit T: Traverse[F],
    HN: HasName[F],
    CV: ConstraintVerify[C, String]
  ): Fix[F] => Vector[Diagnostic] =
    scheme.cata[F, Vector[Diagnostic]] { fa =>
      val nameDiags  = CV.verify(HN.name(fa))
      val childDiags = T.foldLeft(fa, Vector.empty[Diagnostic])(_ ++ _)
      nameDiags ++ childDiags
    }
}
