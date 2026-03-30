package io.alnovis.ircraft.core.framework

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.ops.*

class SemanticMergeSuite extends FunSuite:

  private val merge = SemanticMerge.merge("v1", "v2")
  private val ctx   = PassContext()

  private def module(name: String, ops: Operation*): IrModule =
    IrModule(name, ops.toVector, AttributeMap.empty, None)

  private def iface(name: String, methods: MethodOp*): InterfaceOp =
    InterfaceOp(name = name, methods = methods.toVector)

  private def method(name: String, returnType: TypeRef = TypeRef.STRING): MethodOp =
    MethodOp(name, returnType, modifiers = Set(Modifier.Public, Modifier.Abstract))

  private def file(pkg: String, types: Operation*): FileOp =
    FileOp(pkg, types.toVector)

  private def enumOp(name: String, values: String*): EnumClassOp =
    EnumClassOp(name = name, constants = values.map(v => EnumConstantOp(v)).toVector)

  // -- Basic merge --

  test("two modules with different interfaces -> union"):
    val m1 = module("v1", file("pkg", iface("Money", method("getAmount", TypeRef.LONG))))
    val m2 = module("v2", file("pkg", iface("Currency", method("getCode"))))
    val result = merge.transform(Vector(m1, m2), ctx)
    assert(result.isSuccess)
    val fileOp = result.module.topLevel.head.asInstanceOf[FileOp]
    assertEquals(fileOp.types.size, 2)

  test("two modules with same interface -> merge methods"):
    val m1 = module("v1", file("pkg", iface("Money", method("getAmount", TypeRef.LONG))))
    val m2 = module("v2", file("pkg", iface("Money", method("getAmount", TypeRef.LONG), method("getCurrency"))))
    val result = merge.transform(Vector(m1, m2), ctx)
    val merged = result.module.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(merged.name, "Money")
    assertEquals(merged.methods.size, 2)

  test("method in v1 but not v2 -> merged with merge.presentIn = [v1]"):
    val m1 = module("v1", file("pkg", iface("Money", method("getAmount"), method("getOld"))))
    val m2 = module("v2", file("pkg", iface("Money", method("getAmount"))))
    val result = merge.transform(Vector(m1, m2), ctx)
    val merged = result.module.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val oldMethod = merged.methods.find(_.name == "getOld").get
    assertEquals(oldMethod.attributes.getStringList("merge.presentIn"), Some(List("v1")))

  test("method in both -> merge.presentIn = [v1, v2]"):
    val m1 = module("v1", file("pkg", iface("Money", method("getAmount"))))
    val m2 = module("v2", file("pkg", iface("Money", method("getAmount"))))
    val result = merge.transform(Vector(m1, m2), ctx)
    val merged = result.module.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val m = merged.methods.find(_.name == "getAmount").get
    assertEquals(m.attributes.getStringList("merge.presentIn"), Some(List("v1", "v2")))

  test("method with same name but different type -> diagnostic warning"):
    val m1 = module("v1", file("pkg", iface("Money", method("getAmount", TypeRef.INT))))
    val m2 = module("v2", file("pkg", iface("Money", method("getAmount", TypeRef.LONG))))
    val result = merge.transform(Vector(m1, m2), ctx)
    assert(result.diagnostics.exists(_.message.contains("Type conflict")))
    assert(!result.hasErrors) // warning, not error

  test("enum in v1, not in v2 -> pass through"):
    val m1 = module("v1", file("pkg", enumOp("Status", "ACTIVE", "INACTIVE")))
    val m2 = module("v2", file("pkg"))
    val result = merge.transform(Vector(m1, m2), ctx)
    val types = result.module.topLevel.head.asInstanceOf[FileOp].types
    assertEquals(types.size, 1)
    assertEquals(types.head.asInstanceOf[EnumClassOp].name, "Status")

  test("enums in both -> union constants"):
    val m1 = module("v1", file("pkg", enumOp("Status", "ACTIVE")))
    val m2 = module("v2", file("pkg", enumOp("Status", "ACTIVE", "INACTIVE")))
    val result = merge.transform(Vector(m1, m2), ctx)
    val merged = result.module.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[EnumClassOp]
    assertEquals(merged.constants.size, 2)

  test("empty module + non-empty -> non-empty"):
    val m1 = module("v1")
    val m2 = module("v2", file("pkg", iface("Money", method("getAmount"))))
    val result = merge.transform(Vector(m1, m2), ctx)
    assertEquals(result.module.topLevel.size, 1)

  test("merge.sources attribute on merged FileOp"):
    val m1 = module("v1", file("pkg", iface("Money")))
    val m2 = module("v2", file("pkg", iface("Money")))
    val result = merge.transform(Vector(m1, m2), ctx)
    val fileOp = result.module.topLevel.head.asInstanceOf[FileOp]
    assertEquals(fileOp.attributes.getStringList("merge.sources"), Some(List("v1", "v2")))

  test("module name is combined"):
    val m1 = module("v1", file("pkg", iface("A")))
    val m2 = module("v2", file("pkg", iface("B")))
    val result = merge.transform(Vector(m1, m2), ctx)
    assertEquals(result.module.name, "v1+v2")

  test("nested types merged"):
    val nested1 = iface("Inner", method("getX"))
    val nested2 = iface("Inner", method("getX"), method("getY"))
    val outer1 = InterfaceOp(name = "Outer", nestedTypes = Vector(nested1))
    val outer2 = InterfaceOp(name = "Outer", nestedTypes = Vector(nested2))
    val m1 = module("v1", file("pkg", outer1))
    val m2 = module("v2", file("pkg", outer2))
    val result = merge.transform(Vector(m1, m2), ctx)
    val outer = result.module.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val inner = outer.nestedTypes.head.asInstanceOf[InterfaceOp]
    assertEquals(inner.methods.size, 2)

  test("different packages stay separate"):
    val m1 = module("v1", file("pkg.a", iface("A")))
    val m2 = module("v2", file("pkg.b", iface("B")))
    val result = merge.transform(Vector(m1, m2), ctx)
    assertEquals(result.module.topLevel.size, 2)
