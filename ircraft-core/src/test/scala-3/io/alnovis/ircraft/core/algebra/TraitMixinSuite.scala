package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.core.algebra.SemanticInstances.given
import io.alnovis.ircraft.core.algebra.CoproductInstances.given
import io.alnovis.ircraft.core.algebra.TestDialectF._
import io.alnovis.ircraft.core.algebra.TestDialectF.given
import munit.FunSuite

class TraitMixinSuite extends FunSuite {

  // ---- Step B: SemanticF instances ----

  test("HasName extracts name from TypeDeclF") {
    assertEquals(HasName[SemanticF].name(TypeDeclF("User", TypeKind.Product)), "User")
  }

  test("HasName extracts name from FuncDeclF") {
    assertEquals(HasName[SemanticF].name(FuncDeclF(Func("foo"), Meta.empty)), "foo")
  }

  test("HasName extracts name from EnumDeclF") {
    assertEquals(HasName[SemanticF].name(EnumDeclF("Color")), "Color")
  }

  test("HasName extracts name from AliasDeclF") {
    assertEquals(HasName[SemanticF].name(AliasDeclF("MyType", TypeExpr.STR)), "MyType")
  }

  test("HasName extracts name from ConstDeclF") {
    assertEquals(HasName[SemanticF].name(ConstDeclF("MAX", TypeExpr.INT, Expr.Lit("100", TypeExpr.INT))), "MAX")
  }

  test("HasFields returns fields from TypeDeclF") {
    val f1 = Field("id", TypeExpr.LONG)
    val f2 = Field("name", TypeExpr.STR)
    assertEquals(
      HasFields[SemanticF].fields(TypeDeclF("User", TypeKind.Product, fields = Vector(f1, f2))),
      Vector(f1, f2)
    )
  }

  test("HasFields returns empty for EnumDeclF") {
    assertEquals(HasFields[SemanticF].fields(EnumDeclF("Color")), Vector.empty)
  }

  test("HasFields returns empty for FuncDeclF") {
    assertEquals(HasFields[SemanticF].fields(FuncDeclF(Func("foo"), Meta.empty)), Vector.empty)
  }

  test("HasMethods returns functions from TypeDeclF") {
    val fn = Func("getX", returnType = TypeExpr.INT)
    assertEquals(HasMethods[SemanticF].functions(TypeDeclF("X", TypeKind.Product, functions = Vector(fn))), Vector(fn))
  }

  test("HasMethods returns func from FuncDeclF") {
    val fn = Func("topLevel")
    assertEquals(HasMethods[SemanticF].functions(FuncDeclF(fn, Meta.empty)), Vector(fn))
  }

  test("HasMethods returns empty for ConstDeclF") {
    assertEquals(
      HasMethods[SemanticF].functions(ConstDeclF("X", TypeExpr.INT, Expr.Lit("1", TypeExpr.INT))),
      Vector.empty
    )
  }

  test("HasNested returns nested from TypeDeclF") {
    val child = Fix[SemanticF](TypeDeclF("Inner", TypeKind.Product))
    val td    = TypeDeclF[Fix[SemanticF]]("Outer", TypeKind.Product, nested = Vector(child))
    assertEquals(HasNested[SemanticF].nested(td), Vector(child))
  }

  test("HasNested returns empty for EnumDeclF") {
    assertEquals(HasNested[SemanticF].nested(EnumDeclF[Fix[SemanticF]]("Color")), Vector.empty[Fix[SemanticF]])
  }

  test("HasMeta extracts and updates meta") {
    val key = Meta.Key[String]("test")
    val td  = TypeDeclF[Nothing]("X", TypeKind.Product, meta = Meta.empty.set(key, "hello"))
    assertEquals(HasMeta[SemanticF].meta(td).get(key), Some("hello"))

    val updated = HasMeta[SemanticF].withMeta(td, Meta.empty.set(key, "world"))
    assertEquals(HasMeta[SemanticF].meta(updated).get(key), Some("world"))
  }

  test("HasVisibility extracts visibility from TypeDeclF") {
    assertEquals(
      HasVisibility[SemanticF].visibility(TypeDeclF("X", TypeKind.Product, visibility = Visibility.Private)),
      Visibility.Private
    )
  }

  test("HasVisibility extracts visibility from FuncDeclF") {
    assertEquals(
      HasVisibility[SemanticF].visibility(FuncDeclF(Func("x", visibility = Visibility.Protected), Meta.empty)),
      Visibility.Protected
    )
  }

  // ---- Step C: TestDialectF instances ----

  test("HasName[TestDialectF] on LeafF") {
    assertEquals(HasName[TestDialectF].name(LeafF("x")), "x")
  }

  test("HasName[TestDialectF] on BranchF") {
    assertEquals(HasName[TestDialectF].name(BranchF("root", Vector.empty)), "root")
  }

  test("HasName[TestDialectF] on PairF") {
    assertEquals(HasName[TestDialectF].name(PairF(1, 2)), "pair")
  }

  test("HasName[TestDialectF] on OptionalF") {
    assertEquals(HasName[TestDialectF].name(OptionalF("maybe", Some(1))), "maybe")
  }

  test("HasNested[TestDialectF] on BranchF") {
    assertEquals(HasNested[TestDialectF].nested(BranchF("r", Vector(1, 2, 3))), Vector(1, 2, 3))
  }

  test("HasNested[TestDialectF] on LeafF") {
    assertEquals(HasNested[TestDialectF].nested(LeafF[Int]("x")), Vector.empty[Int])
  }

  test("HasNested[TestDialectF] on PairF") {
    assertEquals(HasNested[TestDialectF].nested(PairF(5, 10)), Vector(5, 10))
  }

  test("HasNested[TestDialectF] on OptionalF Some") {
    assertEquals(HasNested[TestDialectF].nested(OptionalF("x", Some(42))), Vector(42))
  }

  test("HasNested[TestDialectF] on OptionalF None") {
    assertEquals(HasNested[TestDialectF].nested(OptionalF[Int]("x", None)), Vector.empty[Int])
  }

  // ---- Step D: Coproduct auto-derivation ----

  type MixedF = TestDialectF :+: SemanticF

  test("HasName on Coproduct Inl (TestDialectF)") {
    val cp: MixedF[Int] = Coproduct.Inl(LeafF("hello"))
    assertEquals(HasName[MixedF].name(cp), "hello")
  }

  test("HasName on Coproduct Inr (SemanticF)") {
    val cp: MixedF[Int] = Coproduct.Inr(TypeDeclF("User", TypeKind.Product))
    assertEquals(HasName[MixedF].name(cp), "User")
  }

  test("HasNested on Coproduct Inl (TestDialectF)") {
    val cp: MixedF[Int] = Coproduct.Inl(BranchF("r", Vector(1, 2)))
    assertEquals(HasNested[MixedF].nested(cp), Vector(1, 2))
  }

  test("HasNested on Coproduct Inr (SemanticF)") {
    val child                      = Fix[SemanticF](TypeDeclF("Inner", TypeKind.Product))
    val cp: MixedF[Fix[SemanticF]] = Coproduct.Inr(TypeDeclF("Outer", TypeKind.Product, nested = Vector(child)))
    assertEquals(HasNested[MixedF].nested(cp), Vector(child))
  }
}
