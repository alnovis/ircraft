package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.Diagnostic
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir.{ Field, TypeExpr }

/**
  * Generic constraint verification over `Fix[F]` trees via [[scheme.cata]].
  *
  * Provides pre-built verification functions that fold over an entire IR tree
  * and collect [[Diagnostic]] messages for any constraint violations found.
  * These functions are parameterized by the structural typeclasses ([[HasFields]],
  * [[HasName]]) so they work with any dialect functor, not just [[SemanticF]].
  *
  * {{{
  * // Verify that all field types in a tree are resolved:
  * val check: Fix[SemanticF] => Vector[Diagnostic] =
  *   ConstraintVerifier.verifyFieldTypes[SemanticF, MustBeResolved]
  * val errors = check(myTree)
  * }}}
  *
  * @see [[Constraint]] for the constraint marker trait
  * @see [[ConstraintVerify]] for per-value constraint verification
  * @see [[MustBeResolved]] for the standard type-resolution constraint
  * @see [[MustNotBeEmpty]] for the standard non-empty-name constraint
  */
object ConstraintVerifier {

  /**
    * Verifies that all field types in the tree satisfy a given constraint.
    *
    * Folds bottom-up over the `Fix[F]` tree using [[scheme.cata]], extracting
    * fields from each node via [[HasFields]] and verifying each field's type
    * against the constraint via [[ConstraintVerify]]. All violations from all
    * nodes are accumulated into a single result vector.
    *
    * @tparam F the dialect functor type
    * @tparam C the constraint type to verify
    * @param T  implicit `Traverse[F]` for recursive traversal
    * @param HF implicit [[HasFields]][F] for field extraction
    * @param CV implicit [[ConstraintVerify]][C, TypeExpr] for type verification
    * @return a function from `Fix[F]` to a vector of diagnostics (empty on success)
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
    * Verifies that all names in the tree satisfy a given constraint.
    *
    * Folds bottom-up over the `Fix[F]` tree using [[scheme.cata]], extracting
    * the name from each node via [[HasName]] and verifying it against the
    * constraint via [[ConstraintVerify]]. All violations from all nodes are
    * accumulated into a single result vector.
    *
    * @tparam F the dialect functor type
    * @tparam C the constraint type to verify
    * @param T  implicit `Traverse[F]` for recursive traversal
    * @param HN implicit [[HasName]][F] for name extraction
    * @param CV implicit [[ConstraintVerify]][C, String] for name verification
    * @return a function from `Fix[F]` to a vector of diagnostics (empty on success)
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
