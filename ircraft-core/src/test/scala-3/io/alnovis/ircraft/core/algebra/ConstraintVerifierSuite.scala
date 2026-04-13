package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import io.alnovis.ircraft.core.algebra.SemanticInstances.given
import io.alnovis.ircraft.core.algebra.CoproductInstances.given
import io.alnovis.ircraft.core.algebra.TestDialectF._
import io.alnovis.ircraft.core.algebra.TestDialectF.given
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import munit.FunSuite

class ConstraintVerifierSuite extends FunSuite {

  // ---- Step D: verifyFieldTypes ----

  test("verifyFieldTypes passes for all-resolved SemanticF tree") {
    val tree =
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG), Field("name", TypeExpr.STR)))
    val diags = ConstraintVerifier.verifyFieldTypes[SemanticF, MustBeResolved].apply(tree)
    assertEquals(diags, Vector.empty)
  }

  test("verifyFieldTypes finds Unresolved in SemanticF field") {
    val tree =
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("ref", TypeExpr.Unresolved("com.Missing"))))
    val diags = ConstraintVerifier.verifyFieldTypes[SemanticF, MustBeResolved].apply(tree)
    assertEquals(diags.size, 1)
    assert(diags.head.message.contains("com.Missing"), s"got: ${diags.head.message}")
  }

  test("verifyFieldTypes finds Unresolved in nested child") {
    val inner = Decl.typeDecl("Inner", TypeKind.Product, fields = Vector(Field("bad", TypeExpr.Unresolved("X"))))
    val outer = Decl.typeDecl("Outer", TypeKind.Product, nested = Vector(inner))
    val diags = ConstraintVerifier.verifyFieldTypes[SemanticF, MustBeResolved].apply(outer)
    assertEquals(diags.size, 1)
  }

  test("verifyFieldTypes on Coproduct tree") {
    type MixedIR = TestDialectF :+: SemanticF
    val injT = Inject[TestDialectF, MixedIR]
    val injS = Inject[SemanticF, MixedIR]

    // TestDialectF has no fields -- only SemanticF contributes
    // Need HasFields for TestDialectF -- provide empty implementation
    given HasFields[TestDialectF] with
      def fields[A](fa: TestDialectF[A]): Vector[Field] = Vector.empty

    val tree = Fix[MixedIR](
      injT.inj(
        BranchF(
          "root",
          Vector(
            Fix[MixedIR](
              injS.inj(
                TypeDeclF[Fix[MixedIR]](
                  "Bad",
                  TypeKind.Product,
                  fields = Vector(Field("x", TypeExpr.Unresolved("Missing")))
                )
              )
            )
          )
        )
      )
    )

    val diags = ConstraintVerifier.verifyFieldTypes[MixedIR, MustBeResolved].apply(tree)
    assertEquals(diags.size, 1)
  }

  // ---- Step D: verifyNames ----

  test("verifyNames passes for valid names") {
    val tree  = Decl.typeDecl("User", TypeKind.Product, nested = Vector(Decl.typeDecl("Address", TypeKind.Product)))
    val diags = ConstraintVerifier.verifyNames[SemanticF, MustNotBeEmpty].apply(tree)
    assertEquals(diags, Vector.empty)
  }

  test("verifyNames finds empty name") {
    val tree  = Decl.typeDecl("", TypeKind.Product)
    val diags = ConstraintVerifier.verifyNames[SemanticF, MustNotBeEmpty].apply(tree)
    assertEquals(diags.size, 1)
  }

  test("verifyNames on TestDialectF") {
    val tree = Fix[TestDialectF](
      BranchF(
        "root",
        Vector(
          Fix[TestDialectF](LeafF("")),
          Fix[TestDialectF](LeafF("ok"))
        )
      )
    )
    val diags = ConstraintVerifier.verifyNames[TestDialectF, MustNotBeEmpty].apply(tree)
    assertEquals(diags.size, 1)
  }

  // ---- Step F: !> syntax ----

  test("!> infix type compiles and works") {
    val ok: TypeExpr !> MustBeResolved = Constrained(TypeExpr.STR)
    assertEquals(ok.verify, Vector.empty)

    val bad: TypeExpr !> MustBeResolved = Constrained(TypeExpr.Unresolved("X"))
    assertEquals(bad.verify.size, 1)
  }

  test("!> with MustNotBeEmpty") {
    val ok: String !> MustNotBeEmpty = Constrained("hello")
    assertEquals(ok.verify, Vector.empty)

    val bad: String !> MustNotBeEmpty = Constrained("")
    assertEquals(bad.verify.size, 1)
  }

  test("!> with custom constraint") {
    trait MustBeShort extends Constraint
    given ConstraintVerify[MustBeShort, String] =
      ConstraintVerify.instance { s =>
        if (s.length <= 10) Vector.empty
        else Vector(Diagnostic(Severity.Error, s"Too long: ${s.length}"))
      }

    val ok: String !> MustBeShort = Constrained("hi")
    assertEquals(ok.verify, Vector.empty)

    val bad: String !> MustBeShort = Constrained("this is way too long")
    assertEquals(bad.verify.size, 1)
  }
}
