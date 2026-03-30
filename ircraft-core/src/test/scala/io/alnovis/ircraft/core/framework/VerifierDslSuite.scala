package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*

class VerifierDslSuite extends munit.FunSuite:

  private case class StubOp(label: String) extends Operation:
    val kind: NodeKind           = NodeKind("test", "stub")
    val attributes: AttributeMap = AttributeMap.empty
    val regions: Vector[Region]  = Vector.empty
    val span: Option[Span]       = None
    lazy val contentHash: Int    = ContentHash.ofString(label)
    val estimatedSize: Int       = 1

  test("nameNotEmpty: empty name produces error"):
    val diags = VerifierDsl.nameNotEmpty(StubOp("x"), "")
    assertEquals(diags.size, 1)
    assert(diags.head.isError)

  test("nameNotEmpty: non-empty name produces no errors"):
    val diags = VerifierDsl.nameNotEmpty(StubOp("x"), "Foo")
    assert(diags.isEmpty)

  test("fieldNotEmpty: empty value produces error"):
    val diags = VerifierDsl.fieldNotEmpty("  ", "package", None)
    assertEquals(diags.size, 1)

  test("fieldNotEmpty: non-empty value produces no errors"):
    val diags = VerifierDsl.fieldNotEmpty("com.example", "package", None)
    assert(diags.isEmpty)

  test("noDuplicates: no duplicates produces no errors"):
    val diags = VerifierDsl.noDuplicates(List("a", "b", "c"), identity, "field", "message", None)
    assert(diags.isEmpty)

  test("noDuplicates: with duplicates produces errors"):
    val diags = VerifierDsl.noDuplicates(List("a", "b", "a"), identity, "field", "message", None)
    assertEquals(diags.size, 1)
    assert(diags.head.message.contains("Duplicate"))

  test("nonEmpty: empty collection produces diagnostic"):
    val diags = VerifierDsl.nonEmpty(List.empty[Int], "values", None)
    assertEquals(diags.size, 1)
    assert(diags.head.isError)

  test("nonEmpty: non-empty collection produces no diagnostics"):
    val diags = VerifierDsl.nonEmpty(List(1, 2), "values", None)
    assert(diags.isEmpty)

  test("positive: zero and negative produce errors"):
    assertEquals(VerifierDsl.positive(0, "count", None).size, 1)
    assertEquals(VerifierDsl.positive(-1, "count", None).size, 1)
    assert(VerifierDsl.positive(1, "count", None).isEmpty)

  test("notInReservedRange: value in range produces error"):
    val diags = VerifierDsl.notInReservedRange(19500, 19000, 19999, "field number", None)
    assertEquals(diags.size, 1)

  test("notInReservedRange: value outside range produces no error"):
    val diags = VerifierDsl.notInReservedRange(100, 19000, 19999, "field number", None)
    assert(diags.isEmpty)

  test("verifierPass: creates working Pass that recurses into children"):
    val child = StubOp("")
    val parent = new Operation:
      val kind: NodeKind           = NodeKind("test", "parent")
      val attributes: AttributeMap = AttributeMap.empty
      val regions: Vector[Region]  = Vector(Region("body", Vector(child)))
      val span: Option[Span]       = None
      lazy val contentHash: Int    = 42
      val estimatedSize: Int       = 2

    val pass = VerifierDsl.verifierPass("test-verifier"): op =>
      VerifierDsl.nameNotEmpty(op, op.attr("name").map(_.toString).getOrElse("ok"))

    val module = IrModule("test", Vector(parent))
    val result = pass.run(module, PassContext())
    // parent has name "ok" (no attr -> default), child also has "ok" -> no errors
    assert(result.isSuccess)
