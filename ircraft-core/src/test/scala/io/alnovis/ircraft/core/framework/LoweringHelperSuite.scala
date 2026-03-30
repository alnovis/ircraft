package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*

class LoweringHelperSuite extends munit.FunSuite:

  private case class SourceOp(value: String) extends Operation:
    val kind: NodeKind           = NodeKind("source", "op")
    val attributes: AttributeMap = AttributeMap(Attribute.StringAttr("value", value))
    val regions: Vector[Region]  = Vector.empty
    val span: Option[Span]       = None
    lazy val contentHash: Int    = ContentHash.ofString(value)
    val estimatedSize: Int       = 1

  private case class TargetOp(value: String) extends Operation:
    val kind: NodeKind           = NodeKind("target", "op")
    val attributes: AttributeMap = AttributeMap(Attribute.StringAttr("value", value))
    val regions: Vector[Region]  = Vector.empty
    val span: Option[Span]       = None
    lazy val contentHash: Int    = ContentHash.ofString(value)
    val estimatedSize: Int       = 1

  private val srcDialect: Dialect = new Dialect:
    val namespace: String            = "source"
    val description: String          = "source dialect"
    val operationKinds: Set[NodeKind] = Set(NodeKind("source", "op"))

  private val tgtDialect: Dialect = new Dialect:
    val namespace: String            = "target"
    val description: String          = "target dialect"
    val operationKinds: Set[NodeKind] = Set(NodeKind("target", "op"))

  private def makeLowering(
    f: (Operation, PassContext) => Option[Vector[Operation]]
  ): LoweringHelper =
    new LoweringHelper:
      val name: String           = "test-lowering"
      val description: String    = "test"
      val sourceDialect: Dialect = srcDialect
      val targetDialect: Dialect = tgtDialect
      protected def lowerTopLevel(op: Operation, context: PassContext): Option[Vector[Operation]] =
        f(op, context)

  private val ctx = PassContext()

  test("None -> operation passes through unchanged"):
    val lowering = makeLowering((_, _) => None)
    val module   = IrModule("test", Vector(SourceOp("a"), SourceOp("b")))
    val result   = lowering.run(module, ctx)
    assertEquals(result.module.topLevel.size, 2)
    assertEquals(result.module.topLevel(0).asInstanceOf[SourceOp].value, "a")

  test("Some(Vector(...)) -> replaces operation"):
    val lowering = makeLowering:
      case (s: SourceOp, _) => Some(Vector(TargetOp(s.value.toUpperCase)))
      case _                => None
    val module = IrModule("test", Vector(SourceOp("hello")))
    val result = lowering.run(module, ctx)
    assertEquals(result.module.topLevel.size, 1)
    assertEquals(result.module.topLevel(0).asInstanceOf[TargetOp].value, "HELLO")

  test("Some(Vector.empty) -> removes operation"):
    val lowering = makeLowering((_, _) => Some(Vector.empty))
    val module   = IrModule("test", Vector(SourceOp("a"), SourceOp("b")))
    val result   = lowering.run(module, ctx)
    assert(result.module.topLevel.isEmpty)

  test("module metadata is preserved"):
    val lowering = makeLowering((_, _) => None)
    val attrs    = AttributeMap(Attribute.StringAttr("key", "val"))
    val span     = Some(Span("file.proto", 1, 1, 10))
    val module   = IrModule("my-module", Vector(SourceOp("a")), attrs, span)
    val result   = lowering.run(module, ctx)
    assertEquals(result.module.name, "my-module")
    assertEquals(result.module.attributes.getString("key"), Some("val"))
    assertEquals(result.module.span, span)

  test("mixed lowering: some pass through, some replaced"):
    val lowering = makeLowering:
      case (s: SourceOp, _) if s.value == "lower" => Some(Vector(TargetOp("LOWERED")))
      case _                                       => None
    val module = IrModule("test", Vector(SourceOp("keep"), SourceOp("lower"), SourceOp("keep2")))
    val result = lowering.run(module, ctx)
    assertEquals(result.module.topLevel.size, 3)
    assertEquals(result.module.topLevel(0).asInstanceOf[SourceOp].value, "keep")
    assertEquals(result.module.topLevel(1).asInstanceOf[TargetOp].value, "LOWERED")
    assertEquals(result.module.topLevel(2).asInstanceOf[SourceOp].value, "keep2")
