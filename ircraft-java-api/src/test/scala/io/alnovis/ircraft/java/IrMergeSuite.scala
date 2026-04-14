package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.merge.Resolution
import munit.FunSuite

import java.util.{ LinkedHashMap, Map => JMap }

class IrMergeSuite extends FunSuite {

  private def moduleWithField(version: String, fieldType: TypeExpr): IrModule =
    IrModule.of(
      version,
      IrCompilationUnit.of(
        "com.example",
        IrNode.typeDecl("User", TypeKind.Product, TestHelper.jlist(Field("id", fieldType)))
      )
    )

  test("merge single version returns it unchanged") {
    val m1                               = moduleWithField("v1", TypeExpr.Primitive.Int32)
    val versions: JMap[String, IrModule] = new LinkedHashMap()
    versions.put("v1", m1)
    val r = IrMerge.merge(versions, IrMerge.useFirst)
    assert(r.isSuccess)
    assert(r.value.allDeclarations.size > 0)
  }

  test("merge two identical versions succeeds") {
    val m1                               = moduleWithField("v1", TypeExpr.Primitive.Int32)
    val m2                               = moduleWithField("v2", TypeExpr.Primitive.Int32)
    val versions: JMap[String, IrModule] = new LinkedHashMap()
    versions.put("v1", m1)
    versions.put("v2", m2)
    val r = IrMerge.merge(versions, IrMerge.useFirst)
    assert(r.isSuccess)
  }

  test("merge empty map returns error") {
    val r = IrMerge.merge(JMap.of(), IrMerge.useFirst)
    assert(r.isError)
  }

  test("skip strategy skips conflicts") {
    val r = IrMerge.merge(
      JMap.of("v1", moduleWithField("v1", TypeExpr.Primitive.Int32)),
      IrMerge.skip
    )
    assert(r.isSuccess)
  }
}
