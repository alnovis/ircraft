package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import io.alnovis.ircraft.core.ir._
import munit.FunSuite

import java.util.{ List => JList }

class IrPassSuite extends FunSuite {

  private val module =
    IrModule.of("test", IrCompilationUnit.of("com.example", IrNode.typeDecl("User", TypeKind.Product)))

  test("identity pass returns same module") {
    val r = IrPass.identity.apply(module)
    assert(r.isOk)
    assertEquals(r.value, module)
  }

  test("pure pass transforms module") {
    val pass: IrPass = IrPass.pure(
      "rename",
      m => {
        // just verify it runs and returns ok
        m
      }
    )
    assert(pass.apply(module).isOk)
  }

  test("andThen composes two passes") {
    val p1: IrPass = IrPass.pure("a", m => m)
    val p2: IrPass = IrPass.pure("b", m => m)
    val composed   = p1.andThen(p2)
    assert(composed.apply(module).isOk)
  }

  test("andThen: error in first pass stops pipeline") {
    val p1: IrPass = _ => Result.error("fail")
    val p2: IrPass = IrPass.pure("b", m => m)
    val r          = p1.andThen(p2).apply(module)
    assert(r.isError)
  }

  test("andThen: error in second pass returns error") {
    val p1: IrPass = IrPass.pure("a", m => m)
    val p2: IrPass = _ => Result.error("fail")
    val r          = p1.andThen(p2).apply(module)
    assert(r.isError)
  }

  test("andThen: warnings accumulate") {
    val warn1      = Diagnostic(Severity.Warning, "w1")
    val warn2      = Diagnostic(Severity.Warning, "w2")
    val p1: IrPass = m => Result.withWarnings(m, TestHelper.jlist(warn1))
    val p2: IrPass = m => Result.withWarnings(m, TestHelper.jlist(warn2))
    val r          = p1.andThen(p2).apply(module)
    assert(r.isSuccess)
    assertEquals(r.diagnostics.size, 2)
  }

  test("IrPipeline.of composes multiple passes") {
    val p1: IrPass = IrPass.pure("a", m => m)
    val p2: IrPass = IrPass.pure("b", m => m)
    val p3: IrPass = IrPass.pure("c", m => m)
    val r          = IrPipeline.run(IrPipeline.of(p1, p2, p3), module)
    assert(r.isOk)
  }
}
