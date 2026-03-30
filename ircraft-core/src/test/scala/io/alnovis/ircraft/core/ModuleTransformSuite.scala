package io.alnovis.ircraft.core

import munit.FunSuite

class ModuleTransformSuite extends FunSuite:

  private val emptyModule = IrModule("empty", Vector.empty, AttributeMap.empty, None)

  private def module(name: String): IrModule =
    IrModule(name, Vector.empty, AttributeMap.empty, None)

  test("binary factory creates working transform"):
    val merge = ModuleTransform.binary("test-merge"): (m1, m2) =>
      IrModule(s"${m1.name}+${m2.name}", m1.topLevel ++ m2.topLevel, AttributeMap.empty, None)
    val result = merge.transform(Vector(module("a"), module("b")), PassContext())
    assertEquals(result.module.name, "a+b")
    assert(result.isSuccess)

  test("apply factory creates working transform"):
    val concat = ModuleTransform("concat"): modules =>
      IrModule("merged", modules.flatMap(_.topLevel), AttributeMap.empty, None)
    val result = concat.transform(Vector(module("a"), module("b"), module("c")), PassContext())
    assertEquals(result.module.name, "merged")

  test("name is accessible"):
    val t = ModuleTransform.binary("my-merge")((a, _) => a)
    assertEquals(t.name, "my-merge")

  test("transform receives PassContext"):
    var receivedConfig = ""
    val t = new ModuleTransform:
      val name = "ctx-test"
      def transform(inputs: Vector[IrModule], context: PassContext) =
        receivedConfig = context.getOrElse("key", "default")
        PassResult(inputs.head)
    val ctx = PassContext(config = Map("key" -> "value"))
    t.transform(Vector(emptyModule), ctx)
    assertEquals(receivedConfig, "value")

  test("transform can return diagnostics"):
    val t = new ModuleTransform:
      val name = "diag-test"
      def transform(inputs: Vector[IrModule], context: PassContext) =
        PassResult(
          inputs.head,
          List(DiagnosticMessage.warning("conflict detected", None))
        )
    val result = t.transform(Vector(emptyModule), PassContext())
    assertEquals(result.diagnostics.size, 1)
    assert(!result.hasErrors)
