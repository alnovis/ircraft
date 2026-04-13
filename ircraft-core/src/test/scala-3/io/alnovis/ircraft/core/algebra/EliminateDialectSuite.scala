package io.alnovis.ircraft.core.algebra

import cats.Traverse
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.TestDialectF._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import munit.FunSuite

/** Integration test: TestDialectF :+: SemanticF with progressive lowering via eliminate. */
class EliminateDialectSuite extends FunSuite {

  type MixedIR = TestDialectF :+: SemanticF

  // Inject helpers
  val injTest = Inject[TestDialectF, MixedIR]
  val injSem  = Inject[SemanticF, MixedIR]

  // Algebra: lower TestDialectF to SemanticF
  // LeafF(value)         -> ConstDeclF(value, STR, Lit(value))
  // BranchF(label, kids) -> TypeDeclF(label, Product, nested=kids)
  // PairF(l, r)          -> TypeDeclF("pair", Product, nested=Vector(l, r))
  // OptionalF(n, child)  -> TypeDeclF(n, Product, nested=child.toVector)
  val lowerTest: Algebra[TestDialectF, Fix[SemanticF]] = {
    case LeafF(v) =>
      Fix[SemanticF](ConstDeclF(v, TypeExpr.STR, Expr.Lit(v, TypeExpr.STR), Visibility.Public, Meta.empty))
    case BranchF(label, children) =>
      Fix[SemanticF](TypeDeclF(label, TypeKind.Product, nested = children))
    case PairF(l, r) =>
      Fix[SemanticF](TypeDeclF("pair", TypeKind.Product, nested = Vector(l, r)))
    case OptionalF(n, child) =>
      Fix[SemanticF](TypeDeclF(n, TypeKind.Product, nested = child.toVector))
  }

  val doEliminate: Fix[MixedIR] => Fix[SemanticF] = eliminate.dialect(lowerTest)

  // ---- Type safety ----

  test("eliminate returns Fix[SemanticF], not Fix[MixedIR]") {
    val mixed: Fix[MixedIR]    = Fix(injTest.inj(LeafF("x")))
    val result: Fix[SemanticF] = doEliminate(mixed)
    // Compiles = type safe. Check value:
    result.unfix match
      case ConstDeclF(name, _, _, _, _) => assertEquals(name, "x")
      case other                        => fail(s"expected ConstDeclF, got $other")
  }

  // ---- Correctness ----

  test("eliminate converts TestDialectF leaves to SemanticF") {
    val mixed: Fix[MixedIR] = Fix(injTest.inj(LeafF("hello")))
    val result              = doEliminate(mixed)
    result.unfix match
      case ConstDeclF(name, _, _, _, _) => assertEquals(name, "hello")
      case other                        => fail(s"expected ConstDeclF, got $other")
  }

  test("eliminate preserves SemanticF nodes unchanged") {
    // Create SemanticF node directly in MixedIR context
    val mixed: Fix[MixedIR] = Fix(
      injSem.inj(
        TypeDeclF[Fix[MixedIR]]("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG)))
      )
    )
    val result = doEliminate(mixed)
    result.unfix match
      case TypeDeclF(name, _, fields, _, _, _, _, _, _, _) =>
        assertEquals(name, "User")
        assertEquals(fields.size, 1)
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("eliminate handles mixed tree with both dialects") {
    // Branch("root", [Leaf("a"), SemanticF.TypeDecl("B")])
    val leaf = Fix[MixedIR](injTest.inj(LeafF("a")))
    val semNode = Fix[MixedIR](
      injSem.inj(
        TypeDeclF[Fix[MixedIR]]("B", TypeKind.Product)
      )
    )
    val root = Fix[MixedIR](injTest.inj(BranchF("root", Vector(leaf, semNode))))

    val result = doEliminate(root)
    // root -> TypeDeclF("root", nested=[ConstDeclF("a"), TypeDeclF("B")])
    result.unfix match
      case TypeDeclF(name, _, _, _, nested, _, _, _, _, _) =>
        assertEquals(name, "root")
        assertEquals(nested.size, 2)
        assertEquals(SemanticF.name(nested(0).unfix), "a")
        assertEquals(SemanticF.name(nested(1).unfix), "B")
      case other => fail(s"expected TypeDeclF, got $other")
  }

  test("eliminate result works with cata") {
    val tree = Fix[MixedIR](
      injTest.inj(
        BranchF(
          "top",
          Vector(
            Fix[MixedIR](injTest.inj(LeafF("x"))),
            Fix[MixedIR](injTest.inj(LeafF("y")))
          )
        )
      )
    )

    val clean: Fix[SemanticF] = doEliminate(tree)

    // Count nodes via cata
    val countAlg: Algebra[SemanticF, Int] = {
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => 1 + nested.sum
      case _                                            => 1
    }
    assertEquals(scheme.cata(countAlg).apply(clean), 3)
  }

  test("DialectInfo for MixedIR combines both dialects") {
    val info = DialectInfo[MixedIR]
    assertEquals(info.dialectName, "TestDialectF :+: SemanticF")
    assertEquals(info.operationCount, 4 + 5)
  }
}
