package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.ops.*

class IRSuite extends munit.FunSuite:

  private def sampleModule: IrModule =
    val iface = Ops
      .iface("Money")
      .addMethod(Ops.method("getName", Types.STRING).build())
      .build()
    val cls  = Ops.cls("MoneyV1").abstractClass().build()
    val file = Ops.file("com.example.api").addType(iface).addType(cls).build()
    IR.module("test", java.util.List.of(file))

  test("module creation from java.util.List"):
    val m = sampleModule
    assertEquals(m.name, "test")
    assertEquals(m.topLevel.size, 1)

  test("passResult wraps module"):
    val r = IR.passResult(sampleModule)
    assert(r.isSuccess)
    assertEquals(r.diagnostics, Nil)

  test("topLevel returns java.util.List"):
    val ops = IR.topLevel(sampleModule)
    assertEquals(ops.size(), 1)
    assert(ops.get(0).isInstanceOf[FileOp])

  test("types returns types from FileOp"):
    val file  = sampleModule.topLevel.head.asInstanceOf[FileOp]
    val types = IR.types(file)
    assertEquals(types.size(), 2)

  test("methods returns methods from InterfaceOp"):
    val file    = sampleModule.topLevel.head.asInstanceOf[FileOp]
    val iface   = file.types.head.asInstanceOf[InterfaceOp]
    val methods = IR.methods(iface)
    assertEquals(methods.size(), 1)
    assertEquals(methods.get(0).name, "getName")

  test("appendTopLevel adds operations"):
    val extra   = Ops.file("com.example.extra").build()
    val updated = IR.appendTopLevel(sampleModule, java.util.List.of(extra))
    assertEquals(updated.topLevel.size, 2)

  test("collect finds operations by type"):
    val interfaces = IR.collect(sampleModule, classOf[InterfaceOp])
    assertEquals(interfaces.size(), 1)
    assertEquals(interfaces.get(0).name, "Money")

  test("collect finds nested types"):
    val classes = IR.collect(sampleModule, classOf[ClassOp])
    assertEquals(classes.size(), 1)

  test("findApiPackage returns correct package"):
    assertEquals(IR.findApiPackage(sampleModule), "com.example.api")

  test("findApiPackage returns default for empty module"):
    val empty = IR.module("empty", java.util.List.of())
    assertEquals(IR.findApiPackage(empty), "com.example.api")

  test("modifier constants match"):
    assertEquals(IR.PUBLIC, Modifier.Public)
    assertEquals(IR.PRIVATE, Modifier.Private)
    assertEquals(IR.PROTECTED, Modifier.Protected)
    assertEquals(IR.ABSTRACT, Modifier.Abstract)
    assertEquals(IR.STATIC, Modifier.Static)
    assertEquals(IR.FINAL, Modifier.Final)
    assertEquals(IR.OVERRIDE, Modifier.Override)

  test("javadoc accessors"):
    val iface = Ops.iface("Foo").javadoc("docs").build()
    assertEquals(IR.javadoc(iface), "docs")

    val noDoc = Ops.iface("Bar").build()
    assertEquals(IR.javadoc(noDoc), null)

  test("superClass accessor"):
    val cls = Ops.cls("Foo").superClass(Types.named("Bar")).build()
    assertEquals(IR.superClass(cls), Types.named("Bar"))

    val noSuper = Ops.cls("Baz").build()
    assertEquals(IR.superClass(noSuper), null)

  test("transform applies function to matching operations"):
    val m = sampleModule
    // Add a method to all InterfaceOps
    val transformed = IR.transform(
      m,
      op =>
        op match
          case iface: InterfaceOp =>
            Ops
              .rebuildIface(iface)
              .addMethod(Ops.method("extra", Types.VOID).abstractPublic().build())
              .build()
          case other => other
    )
    val file  = transformed.topLevel.head.asInstanceOf[FileOp]
    val iface = file.types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(1).name, "extra")

  test("transform is bottom-up -- children transformed before parent"):
    // Create nested structure: FileOp contains InterfaceOp with nestedType ClassOp
    val nested = Ops.cls("Inner").build()
    val iface  = Ops.iface("Outer").addNestedType(nested).build()
    val file   = Ops.file("com.example").addType(iface).build()
    val m      = IR.module("test", java.util.List.of(file))

    val order = new java.util.ArrayList[String]()
    IR.transform(
      m,
      op =>
        op match
          case c: ClassOp =>
            order.add("ClassOp:" + c.name)
            op
          case i: InterfaceOp =>
            order.add("InterfaceOp:" + i.name)
            op
          case _ => op
    )
    // Bottom-up: ClassOp (child) should be visited before InterfaceOp (parent)
    assert(
      order.indexOf("ClassOp:Inner") < order.indexOf("InterfaceOp:Outer"),
      s"Expected Inner before Outer, got: $order"
    )
