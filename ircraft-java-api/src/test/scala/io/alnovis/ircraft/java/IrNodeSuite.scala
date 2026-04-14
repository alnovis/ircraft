package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._
import munit.FunSuite

import java.util.{ List => JList }

class IrNodeSuite extends FunSuite {

  private val strKey = Meta.Key[String]("test")

  test("typeDecl minimal: name and kind") {
    val node = IrNode.typeDecl("User", TypeKind.Product)
    assertEquals(node.name, "User")
    assertEquals(node.kind, DeclKind.TypeDecl)
  }

  test("typeDecl with fields") {
    val fields = JList.of(Field("id", TypeExpr.Primitive.Int64), Field("name", TypeExpr.Primitive.Str))
    val node   = IrNode.typeDecl("User", TypeKind.Product, fields)
    val view   = node.asTypeDecl.get()
    assertEquals(view.fields.size, 2)
    assertEquals(view.fields.get(0).name, "id")
  }

  test("enumDecl") {
    val variants = JList.of(EnumVariant("A"), EnumVariant("B"))
    val node     = IrNode.enumDecl("Status", variants)
    assertEquals(node.name, "Status")
    assertEquals(node.kind, DeclKind.EnumDecl)
    val view = node.asEnumDecl.get()
    assertEquals(view.variants.size, 2)
  }

  test("funcDecl") {
    val func = Func("doWork")
    val node = IrNode.funcDecl(func)
    assertEquals(node.name, "doWork")
    assertEquals(node.kind, DeclKind.FuncDecl)
    val view = node.asFuncDecl.get()
    assertEquals(view.func.name, "doWork")
  }

  test("aliasDecl") {
    val node = IrNode.aliasDecl("UserId", TypeExpr.Primitive.Str)
    assertEquals(node.name, "UserId")
    assertEquals(node.kind, DeclKind.AliasDecl)
    val view = node.asAliasDecl.get()
    assertEquals(view.target, TypeExpr.Primitive.Str)
  }

  test("constDecl") {
    val node = IrNode.constDecl("MAX", TypeExpr.Primitive.Int32, Expr.Lit("100", TypeExpr.Primitive.Int32))
    assertEquals(node.name, "MAX")
    assertEquals(node.kind, DeclKind.ConstDecl)
  }

  test("asTypeDecl returns empty for non-TypeDecl") {
    val node = IrNode.enumDecl("E", JList.of())
    assert(node.asTypeDecl.isEmpty)
  }

  test("asEnumDecl returns empty for non-EnumDecl") {
    val node = IrNode.typeDecl("T", TypeKind.Product)
    assert(node.asEnumDecl.isEmpty)
  }

  test("meta and withMeta") {
    val node    = IrNode.typeDecl("X", TypeKind.Product)
    val updated = node.withMeta(IrMeta.of(strKey, "val"))
    assertEquals(updated.meta.get(strKey).orElse(""), "val")
    // original unchanged
    assert(node.meta.get(strKey).isEmpty)
  }

  test("nested declarations") {
    val inner = IrNode.typeDecl("Inner", TypeKind.Product)
    val outer = IrNode.typeDecl(
      "Outer",
      TypeKind.Product,
      JList.of(),
      JList.of(),
      TestHelper.jlist(inner),
      JList.of(),
      JList.of(),
      Visibility.Public,
      JList.of(),
      IrMeta.empty
    )
    val view = outer.asTypeDecl.get()
    assertEquals(view.nested.size, 1)
    assertEquals(view.nested.get(0).name, "Inner")
  }

  test("roundtrip toScala -> fromScala") {
    val node         = IrNode.typeDecl("User", TypeKind.Product)
    val roundtripped = IrNode.fromScala(node.toScala)
    assertEquals(roundtripped.name, "User")
    assertEquals(roundtripped, node)
  }

  test("toString contains kind and name") {
    val node = IrNode.typeDecl("User", TypeKind.Product)
    assert(node.toString.contains("TypeDecl"))
    assert(node.toString.contains("User"))
  }

  test("equals: same content") {
    val a = IrNode.typeDecl("X", TypeKind.Product)
    val b = IrNode.typeDecl("X", TypeKind.Product)
    assertEquals(a, b)
  }

  test("equals: different content") {
    val a = IrNode.typeDecl("X", TypeKind.Product)
    val b = IrNode.typeDecl("Y", TypeKind.Product)
    assertNotEquals(a, b)
  }
}
