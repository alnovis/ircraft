package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import io.alnovis.ircraft.core.ir._
import munit.FunSuite

class ConstraintSuite extends FunSuite {

  // ---- Step A: foundation ----

  test("ConstraintVerify.instance creates working verifier") {
    trait AlwaysFail extends Constraint
    implicit val v: ConstraintVerify[AlwaysFail, Int] =
      ConstraintVerify.instance(_ => Vector(Diagnostic(Severity.Error, "fail")))
    assertEquals(ConstraintVerify[AlwaysFail, Int].verify(42).size, 1)
  }

  // ---- Step B: MustBeResolved ----

  test("MustBeResolved passes for resolved type") {
    assertEquals(MustBeResolved.verifyTypeExpr.verify(TypeExpr.STR), Vector.empty)
  }

  test("MustBeResolved fails for Unresolved") {
    val diags = MustBeResolved.verifyTypeExpr.verify(TypeExpr.Unresolved("com.Foo"))
    assertEquals(diags.size, 1)
    assert(diags.head.message.contains("com.Foo"))
  }

  test("MustBeResolved finds Unresolved inside Optional") {
    val diags = MustBeResolved.verifyTypeExpr.verify(TypeExpr.Optional(TypeExpr.Unresolved("X")))
    assertEquals(diags.size, 1)
  }

  test("MustBeResolved finds Unresolved inside MapOf value") {
    val diags = MustBeResolved.verifyTypeExpr.verify(TypeExpr.MapOf(TypeExpr.STR, TypeExpr.Unresolved("Y")))
    assertEquals(diags.size, 1)
  }

  test("MustBeResolved passes for ListOf(Named)") {
    assertEquals(MustBeResolved.verifyTypeExpr.verify(TypeExpr.ListOf(TypeExpr.Named("OK"))), Vector.empty)
  }

  test("MustBeResolved finds deeply nested Unresolved") {
    val deep  = TypeExpr.MapOf(TypeExpr.STR, TypeExpr.ListOf(TypeExpr.Optional(TypeExpr.Unresolved("Deep"))))
    val diags = MustBeResolved.verifyTypeExpr.verify(deep)
    assertEquals(diags.size, 1)
    assert(diags.head.message.contains("Deep"))
  }

  // ---- Step C: MustNotBeEmpty ----

  test("MustNotBeEmpty passes for non-empty string") {
    assertEquals(MustNotBeEmpty.verifyString.verify("hello"), Vector.empty)
  }

  test("MustNotBeEmpty fails for empty string") {
    assertEquals(MustNotBeEmpty.verifyString.verify("").size, 1)
  }

  test("MustNotBeEmpty fails for whitespace-only string") {
    assertEquals(MustNotBeEmpty.verifyString.verify("  ").size, 1)
  }

  test("MustNotBeEmpty passes for single char") {
    assertEquals(MustNotBeEmpty.verifyString.verify("a"), Vector.empty)
  }

  // ---- Step E: Custom constraint ----

  test("user-defined custom constraint works") {
    trait MustBePositive extends Constraint

    implicit val verifyInt: ConstraintVerify[MustBePositive, Int] =
      ConstraintVerify.instance { n =>
        if (n > 0) Vector.empty
        else Vector(Diagnostic(Severity.Error, s"Value must be positive, got $n"))
      }

    assertEquals(ConstraintVerify[MustBePositive, Int].verify(42), Vector.empty)
    assertEquals(ConstraintVerify[MustBePositive, Int].verify(0).size, 1)
    assertEquals(ConstraintVerify[MustBePositive, Int].verify(-1).size, 1)
  }

  // ---- Step F: Constrained wrapper ----

  test("Constrained.verify delegates to ConstraintVerify") {
    val ok = Constrained[TypeExpr, MustBeResolved](TypeExpr.STR)
    assertEquals(ok.verify, Vector.empty)

    val bad = Constrained[TypeExpr, MustBeResolved](TypeExpr.Unresolved("X"))
    assertEquals(bad.verify.size, 1)
  }

  test("Constrained.verify with MustNotBeEmpty") {
    val ok = Constrained[String, MustNotBeEmpty]("hello")
    assertEquals(ok.verify, Vector.empty)

    val bad = Constrained[String, MustNotBeEmpty]("")
    assertEquals(bad.verify.size, 1)
  }
}
