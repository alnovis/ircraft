package io.alnovis.ircraft.core.algebra

import cats._
import cats.data.{ Ior, NonEmptyChain }
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import munit.FunSuite

class FindUnresolvedSuite extends FunSuite {

  // ---- Step A: findUnresolvedAlg tests ----

  test("TypeDeclF with all resolved fields returns empty") {
    val tree =
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG), Field("name", TypeExpr.STR)))
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector.empty)
  }

  test("TypeDeclF with Unresolved in field type") {
    val tree =
      Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("address", TypeExpr.Unresolved("com.Foo"))))
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector(("User", "address", "com.Foo")))
  }

  test("TypeDeclF with Unresolved in function return type") {
    val tree = Decl.typeDecl(
      "Service",
      TypeKind.Protocol,
      functions = Vector(Func("find", returnType = TypeExpr.Unresolved("com.Entity")))
    )
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector(("Service", "find", "com.Entity")))
  }

  test("TypeDeclF with Unresolved in function param type") {
    val tree = Decl.typeDecl(
      "Service",
      TypeKind.Protocol,
      functions = Vector(Func("save", params = Vector(Param("entity", TypeExpr.Unresolved("com.Foo")))))
    )
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector(("Service", "save.entity", "com.Foo")))
  }

  test("EnumDeclF with Unresolved in function") {
    val tree = Decl.enumDecl("Status", functions = Vector(Func("toEntity", returnType = TypeExpr.Unresolved("com.X"))))
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector(("Status", "toEntity", "com.X")))
  }

  test("FuncDeclF returns empty (no fields/nested)") {
    val tree = Decl.funcDecl(Func("topLevel", returnType = TypeExpr.STR))
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector.empty)
  }

  test("Nested TypeDeclF -- child errors bubble up via cata") {
    val inner =
      Decl.typeDecl("Inner", TypeKind.Product, fields = Vector(Field("ref", TypeExpr.Unresolved("com.Missing"))))
    val outer  = Decl.typeDecl("Outer", TypeKind.Product, nested = Vector(inner))
    val errors = scheme.cata(Passes.findUnresolvedAlg).apply(outer)
    assertEquals(errors, Vector(("Inner", "ref", "com.Missing")))
  }

  test("Unresolved inside Optional") {
    val tree = Decl.typeDecl(
      "X",
      TypeKind.Product,
      fields = Vector(Field("opt", TypeExpr.Optional(TypeExpr.Unresolved("com.Y"))))
    )
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector(("X", "opt", "com.Y")))
  }

  test("Unresolved inside MapOf value") {
    val tree = Decl.typeDecl(
      "X",
      TypeKind.Product,
      fields = Vector(Field("map", TypeExpr.MapOf(TypeExpr.STR, TypeExpr.Unresolved("com.Z"))))
    )
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector(("X", "map", "com.Z")))
  }

  test("Empty TypeDeclF returns empty") {
    val tree = Decl.typeDecl("Empty", TypeKind.Product)
    assertEquals(scheme.cata(Passes.findUnresolvedAlg).apply(tree), Vector.empty)
  }

  test("Multiple errors collected") {
    val tree = Decl.typeDecl(
      "Multi",
      TypeKind.Product,
      fields = Vector(
        Field("a", TypeExpr.Unresolved("X")),
        Field("b", TypeExpr.Unresolved("Y"))
      ),
      functions = Vector(Func("f", returnType = TypeExpr.Unresolved("Z")))
    )
    val errors = scheme.cata(Passes.findUnresolvedAlg).apply(tree)
    assertEquals(errors.size, 3)
    assertEquals(errors.map(_._3).toSet, Set("X", "Y", "Z"))
  }

  // ---- Step B: validateResolved via cata (existing tests must pass) ----

  test("validateResolved detects unresolved types") {
    val module = Module(
      "test",
      Vector(
        CompilationUnit(
          "com.example",
          Vector(
            Decl.typeDecl(
              "Order",
              TypeKind.Product,
              fields = Vector(Field("address", TypeExpr.Unresolved("com.example.Address")))
            )
          )
        )
      )
    )
    Pipeline.run(Passes.validateResolved[Id], module).value match
      case Ior.Left(errors) =>
        assert(errors.exists(d => d.isError && d.message.contains("Unresolved")), "expected Unresolved error")
      case other => fail(s"expected Ior.Left, got $other")
  }

  test("validateResolved passes clean module") {
    val module = Module(
      "test",
      Vector(
        CompilationUnit(
          "com.example",
          Vector(
            Decl.typeDecl("User", TypeKind.Product, fields = Vector(Field("name", TypeExpr.STR)))
          )
        )
      )
    )
    Pipeline.run(Passes.validateResolved[Id], module).value match
      case Ior.Right(m) => assertEquals(m.units.size, 1)
      case other        => fail(s"expected Ior.Right, got $other")
  }

  test("validateResolved detects nested unresolved") {
    val inner  = Decl.typeDecl("Inner", TypeKind.Product, fields = Vector(Field("x", TypeExpr.Unresolved("Missing"))))
    val outer  = Decl.typeDecl("Outer", TypeKind.Product, nested = Vector(inner))
    val module = Module("test", Vector(CompilationUnit("pkg", Vector(outer))))
    Pipeline.run(Passes.validateResolved[Id], module).value match
      case Ior.Left(errors) =>
        assert(errors.exists(_.message.contains("Missing")), "expected Missing error")
      case other => fail(s"expected Ior.Left, got $other")
  }

  test("validateResolved collects multiple errors") {
    val module = Module(
      "test",
      Vector(
        CompilationUnit(
          "pkg",
          Vector(
            Decl.typeDecl("A", TypeKind.Product, fields = Vector(Field("x", TypeExpr.Unresolved("X")))),
            Decl.typeDecl("B", TypeKind.Product, fields = Vector(Field("y", TypeExpr.Unresolved("Y"))))
          )
        )
      )
    )
    Pipeline.run(Passes.validateResolved[Id], module).value match
      case Ior.Left(errors) => assertEquals(errors.length.toInt, 2)
      case other            => fail(s"expected Ior.Left with 2 errors, got $other")
  }

  // ---- Step C: Stack safety ----

  test("validateResolved is stack-safe on 50k-deep tree") {
    var tree: Fix[SemanticF] = Decl.typeDecl("leaf", TypeKind.Product, fields = Vector(Field("ok", TypeExpr.STR)))
    for (_ <- 0 until 50000) {
      tree = Decl.typeDecl("node", TypeKind.Product, nested = Vector(tree))
    }
    val module = Module("test", Vector(CompilationUnit("pkg", Vector(tree))))
    Pipeline.run(Passes.validateResolved[Id], module).value match
      case Ior.Right(_) => () // pass
      case other        => fail(s"expected Ior.Right, got $other")
  }

  test("stack-safe: finds Unresolved at deepest level of 50k tree") {
    var tree: Fix[SemanticF] =
      Decl.typeDecl("deep", TypeKind.Product, fields = Vector(Field("broken", TypeExpr.Unresolved("com.Deep"))))
    for (_ <- 0 until 50000) {
      tree = Decl.typeDecl("node", TypeKind.Product, nested = Vector(tree))
    }
    val module = Module("test", Vector(CompilationUnit("pkg", Vector(tree))))
    Pipeline.run(Passes.validateResolved[Id], module).value match
      case Ior.Left(errors) =>
        assert(errors.exists(_.message.contains("com.Deep")), "expected com.Deep error")
      case other => fail(s"expected Ior.Left, got $other")
  }
}
