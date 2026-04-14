package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._
import munit.FunSuite

import java.util.{ List => JList }

class IrModuleSuite extends FunSuite {

  private val node1 = IrNode.typeDecl("User", TypeKind.Product)
  private val node2 = IrNode.typeDecl("Order", TypeKind.Product)
  private val unit1 = IrCompilationUnit.of("com.example.model", node1, node2)
  private val unit2 = IrCompilationUnit.of("com.example.api", IrNode.typeDecl("Api", TypeKind.Protocol))

  test("IrModule.of: name and units") {
    val m = IrModule.of("test", unit1, unit2)
    assertEquals(m.name, "test")
    assertEquals(m.units.size, 2)
  }

  test("IrModule.empty: no units") {
    val m = IrModule.empty("empty")
    assertEquals(m.name, "empty")
    assert(m.units.isEmpty)
  }

  test("IrModule.allDeclarations: flattens all units") {
    val m = IrModule.of("test", unit1, unit2)
    assertEquals(m.allDeclarations.size, 3) // 2 + 1
  }

  test("IrCompilationUnit.of: namespace and declarations") {
    assertEquals(unit1.namespace, "com.example.model")
    assertEquals(unit1.declarations.size, 2)
    assertEquals(unit1.declarations.get(0).name, "User")
  }

  test("IrCompilationUnit.of with JList") {
    val cu = IrCompilationUnit.of("pkg", TestHelper.jlist(node1))
    assertEquals(cu.declarations.size, 1)
  }

  test("roundtrip toScala -> fromScala for Module") {
    val m  = IrModule.of("test", unit1)
    val rt = IrModule.fromScala(m.toScala)
    assertEquals(rt.name, "test")
    assertEquals(rt.allDeclarations.size, 2)
  }

  test("roundtrip toScala -> fromScala for CompilationUnit") {
    val rt = IrCompilationUnit.fromScala(unit1.toScala)
    assertEquals(rt.namespace, "com.example.model")
    assertEquals(rt.declarations.size, 2)
  }

  test("IrModule.of with JList") {
    val m = IrModule.of("test", JList.of(unit1, unit2))
    assertEquals(m.units.size, 2)
  }

  test("meta on module") {
    assert(IrModule.empty("x").meta.isEmpty)
  }

  test("meta on compilation unit") {
    assert(unit1.meta.isEmpty)
  }

  test("equals: same content") {
    val a = IrModule.of("x", unit1)
    val b = IrModule.of("x", unit1)
    assertEquals(a, b)
  }
}
