package io.alnovis.ircraft.dialect.proto

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass

class ProtoVerifierSuite extends FunSuite:

  private def verify(ops: Operation*): List[DiagnosticMessage] =
    val module = IrModule("test", ops.toVector, AttributeMap.empty, None)
    ProtoVerifierPass.run(module, PassContext()).diagnostics

  test("valid message passes verification"):
    val file = ProtoFileOp(
      "com.example",
      messages = Vector(
        MessageOp("Money", fields = Vector(
          FieldOp("amount", 1, TypeRef.LONG),
          FieldOp("currency", 2, TypeRef.STRING)
        ))
      ),
      enums = Vector(
        EnumOp("Status", values = Vector(EnumValueOp("UNKNOWN", 0)))
      )
    )
    val diags = verify(file)
    assert(diags.forall(!_.isError), s"Expected no errors but got: $diags")

  test("empty message name is error"):
    val diags = verify(ProtoFileOp("pkg", messages = Vector(MessageOp(""))))
    assert(diags.exists(d => d.isError && d.message.contains("name must not be empty")))

  test("field number <= 0 is error"):
    val msg = MessageOp("Test", fields = Vector(FieldOp("bad", 0, TypeRef.INT)))
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("invalid number")))

  test("reserved field number range is error"):
    val msg = MessageOp("Test", fields = Vector(FieldOp("reserved", 19500, TypeRef.INT)))
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("reserved number")))

  test("duplicate field numbers is error"):
    val msg = MessageOp("Test", fields = Vector(
      FieldOp("a", 1, TypeRef.INT),
      FieldOp("b", 1, TypeRef.STRING)
    ))
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("Duplicate field number")))

  test("duplicate field names is error"):
    val msg = MessageOp("Test", fields = Vector(
      FieldOp("name", 1, TypeRef.INT),
      FieldOp("name", 2, TypeRef.STRING)
    ))
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("Duplicate field name")))

  test("duplicate field numbers across message and oneof"):
    val msg = MessageOp(
      "Test",
      fields = Vector(FieldOp("a", 1, TypeRef.INT)),
      oneofs = Vector(OneofOp("choice", fields = Vector(FieldOp("b", 1, TypeRef.STRING))))
    )
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("Duplicate field number")))

  test("empty enum name is error"):
    val diags = verify(ProtoFileOp("pkg", enums = Vector(EnumOp(""))))
    assert(diags.exists(d => d.isError && d.message.contains("Enum name must not be empty")))

  test("empty enum values is warning"):
    val diags = verify(ProtoFileOp("pkg", enums = Vector(EnumOp("Empty"))))
    assert(diags.exists(d => d.severity == Severity.Warning && d.message.contains("no values")))

  test("duplicate enum value numbers is error"):
    val e = EnumOp("Bad", values = Vector(EnumValueOp("A", 0), EnumValueOp("B", 0)))
    val diags = verify(ProtoFileOp("pkg", enums = Vector(e)))
    assert(diags.exists(d => d.isError && d.message.contains("Duplicate enum value number")))

  test("empty oneof name is error"):
    val msg = MessageOp("Test", oneofs = Vector(OneofOp("")))
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("Oneof name must not be empty")))

  test("nested message validation"):
    val msg = MessageOp(
      "Outer",
      nestedMessages = Vector(
        MessageOp("", fields = Vector(FieldOp("x", 1, TypeRef.INT)))
      )
    )
    val diags = verify(ProtoFileOp("pkg", messages = Vector(msg)))
    assert(diags.exists(d => d.isError && d.message.contains("name must not be empty")))
