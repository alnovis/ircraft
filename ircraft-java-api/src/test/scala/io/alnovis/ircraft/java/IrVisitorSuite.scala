package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._
import munit.FunSuite

import java.util.{ List => JList }
import scala.jdk.CollectionConverters._

class IrVisitorSuite extends FunSuite {

  /** Counts total nodes. */
  private val counter = new IrVisitor[Int] {
    def visitTypeDecl(decl: TypeDeclView, nestedResults: JList[Int]): Int =
      1 + nestedResults.asScala.sum
    def visitEnumDecl(decl: EnumDeclView): Int   = 1
    def visitFuncDecl(decl: FuncDeclView): Int   = 1
    def visitAliasDecl(decl: AliasDeclView): Int = 1
    def visitConstDecl(decl: ConstDeclView): Int = 1
  }

  /** Collects all names in DFS order. */
  private val nameCollector = new IrVisitor[JList[String]] {
    def visitTypeDecl(decl: TypeDeclView, nestedResults: JList[JList[String]]): JList[String] = {
      val result = new java.util.ArrayList[String]()
      result.add(decl.name)
      nestedResults.forEach(ns => result.addAll(ns))
      result
    }
    def visitEnumDecl(decl: EnumDeclView): JList[String]   = TestHelper.jlist(decl.name)
    def visitFuncDecl(decl: FuncDeclView): JList[String]   = TestHelper.jlist(decl.func.name)
    def visitAliasDecl(decl: AliasDeclView): JList[String] = TestHelper.jlist(decl.name)
    def visitConstDecl(decl: ConstDeclView): JList[String] = TestHelper.jlist(decl.name)
  }

  test("counter: single TypeDecl") {
    val node = IrNode.typeDecl("User", TypeKind.Product)
    assertEquals(counter.visit(node), 1)
  }

  test("counter: TypeDecl with nested") {
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
    assertEquals(counter.visit(outer), 2)
  }

  test("counter: EnumDecl") {
    val node = IrNode.enumDecl("Status", JList.of())
    assertEquals(counter.visit(node), 1)
  }

  test("counter: FuncDecl") {
    val node = IrNode.funcDecl(Func("doWork"))
    assertEquals(counter.visit(node), 1)
  }

  test("counter: AliasDecl") {
    val node = IrNode.aliasDecl("UserId", TypeExpr.Primitive.Str)
    assertEquals(counter.visit(node), 1)
  }

  test("counter: ConstDecl") {
    val node = IrNode.constDecl("MAX", TypeExpr.Primitive.Int32, Expr.Lit("100", TypeExpr.Primitive.Int32))
    assertEquals(counter.visit(node), 1)
  }

  test("nameCollector: DFS order") {
    val inner1 = IrNode.typeDecl("A", TypeKind.Product)
    val inner2 = IrNode.typeDecl("B", TypeKind.Product)
    val outer = IrNode.typeDecl(
      "Root",
      TypeKind.Product,
      JList.of(),
      JList.of(),
      JList.of(inner1, inner2),
      JList.of(),
      JList.of(),
      Visibility.Public,
      JList.of(),
      IrMeta.empty
    )
    val names = nameCollector.visit(outer)
    assertEquals(names, JList.of("Root", "A", "B"))
  }

  test("nameCollector: deeply nested") {
    val leaf = IrNode.typeDecl("Leaf", TypeKind.Product)
    val mid = IrNode.typeDecl(
      "Mid",
      TypeKind.Product,
      JList.of(),
      JList.of(),
      TestHelper.jlist(leaf),
      JList.of(),
      JList.of(),
      Visibility.Public,
      JList.of(),
      IrMeta.empty
    )
    val root = IrNode.typeDecl(
      "Root",
      TypeKind.Product,
      JList.of(),
      JList.of(),
      TestHelper.jlist(mid),
      JList.of(),
      JList.of(),
      Visibility.Public,
      JList.of(),
      IrMeta.empty
    )
    val names = nameCollector.visit(root)
    assertEquals(names, JList.of("Root", "Mid", "Leaf"))
  }
}
