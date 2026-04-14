package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.dialects.proto._
import munit.FunSuite

import java.util.{ List => JList }

class IrLoweringSuite extends FunSuite {

  test("IrLowering.pure creates lowering") {
    val lowering = IrLowering.pure[String](s =>
      IrModule.of("test", IrCompilationUnit.of("pkg", IrNode.typeDecl(s, TypeKind.Product)))
    )
    val r = lowering.lower("User")
    assert(r.isOk)
    assertEquals(r.value.allDeclarations.get(0).name, "User")
  }

  test("ProtoLoweringFacade.lower on simple proto") {
    val proto = ProtoFile(
      "test.proto",
      ProtoSyntax.Proto3,
      "test",
      Some("com.example.test"),
      None,
      true,
      Vector(
        ProtoMessage(
          "SimpleMsg",
          Vector(
            ProtoField("id", 1, ProtoType.Int64, ProtoLabel.Optional, None)
          ),
          Vector.empty,
          Vector.empty,
          Vector.empty
        )
      ),
      Vector.empty
    )
    val r = ProtoLoweringFacade.lower(proto)
    assert(r.isOk)
    assert(r.value.allDeclarations.size > 0)
  }

  test("ProtoLoweringFacade.lowerAll on multiple protos") {
    val proto1 = ProtoFile(
      "a.proto",
      ProtoSyntax.Proto3,
      "a",
      Some("com.a"),
      None,
      true,
      Vector(ProtoMessage("MsgA", Vector.empty, Vector.empty, Vector.empty, Vector.empty)),
      Vector.empty
    )
    val proto2 = ProtoFile(
      "b.proto",
      ProtoSyntax.Proto3,
      "b",
      Some("com.b"),
      None,
      true,
      Vector(ProtoMessage("MsgB", Vector.empty, Vector.empty, Vector.empty, Vector.empty)),
      Vector.empty
    )
    val r = ProtoLoweringFacade.lowerAll(JList.of(proto1, proto2))
    assert(r.isOk)
    assert(r.value.allDeclarations.size >= 2)
  }
}
