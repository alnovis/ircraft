package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._
import munit.FunSuite

class IrEmitterSuite extends FunSuite {

  private val module = IrModule.of(
    "test",
    IrCompilationUnit.of(
      "com.example",
      IrNode.typeDecl(
        "User",
        TypeKind.Product,
        java.util.List.of(Field("id", TypeExpr.Primitive.Int64), Field("name", TypeExpr.Primitive.Str))
      )
    )
  )

  test("java emitter produces .java files") {
    val r = IrEmitter.java(module)
    assert(r.isOk)
    val files = r.value
    assert(files.size > 0)
    val key = files.keySet.iterator.next
    assert(key.toString.endsWith(".java"))
  }

  test("scala3 emitter produces .scala files") {
    val r = IrEmitter.scala3(module)
    assert(r.isOk)
    assert(r.value.size > 0)
    val key = r.value.keySet.iterator.next
    assert(key.toString.endsWith(".scala"))
  }

  test("scala2 emitter produces .scala files") {
    val r = IrEmitter.scala2(module)
    assert(r.isOk)
    assert(r.value.size > 0)
  }

  test("emitting empty module produces empty map") {
    val r = IrEmitter.java(IrModule.empty("empty"))
    assert(r.isOk)
    assert(r.value.isEmpty)
  }

  test("java emitter output contains class keyword") {
    val r      = IrEmitter.java(module)
    val source = r.value.values.iterator.next
    assert(source.contains("class User"))
  }
}
