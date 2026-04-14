package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.{ Diagnostic, Severity }
import munit.FunSuite

import java.util.{ Collections, List => JList }

class ResultSuite extends FunSuite {

  import TestHelper.jlist

  private val warn = Diagnostic(Severity.Warning, "deprecation")
  private val err  = Diagnostic(Severity.Error, "unresolved type")

  // -- ok --

  test("ok: isOk true, isError false, hasWarnings false") {
    val r = Result.ok(42)
    assert(r.isOk)
    assert(r.isSuccess)
    assert(!r.isError)
    assert(!r.hasWarnings)
  }

  test("ok: value returns value") {
    assertEquals(Result.ok(42).value, 42)
  }

  test("ok: diagnostics empty") {
    assert(Result.ok(42).diagnostics.isEmpty)
  }

  test("ok: valueOpt present") {
    assert(Result.ok(42).valueOpt.isPresent)
  }

  // -- withWarnings --

  test("withWarnings: hasWarnings true, isOk false, isError false, isSuccess true") {
    val r = Result.withWarnings(42, jlist(warn))
    assert(r.hasWarnings)
    assert(!r.isOk)
    assert(!r.isError)
    assert(r.isSuccess)
  }

  test("withWarnings: value returns value") {
    assertEquals(Result.withWarnings(42, jlist(warn)).value, 42)
  }

  test("withWarnings: diagnostics contains warning") {
    val r = Result.withWarnings(42, jlist(warn))
    assertEquals(r.diagnostics.size, 1)
    assert(r.diagnostics.get(0).isWarning)
  }

  test("withWarnings: empty list degrades to ok") {
    val r = Result.withWarnings(42, JList.of())
    assert(r.isOk)
  }

  // -- error --

  test("error: isError true, isOk false, hasWarnings false") {
    val r = Result.error[Int](jlist(err))
    assert(r.isError)
    assert(!r.isOk)
    assert(!r.hasWarnings)
    assert(!r.isSuccess)
  }

  test("error: value throws") {
    intercept[NoSuchElementException] {
      Result.error[Int]("fail").value
    }
  }

  test("error: valueOpt empty") {
    assert(Result.error[Int]("fail").valueOpt.isEmpty)
  }

  test("error: diagnostics contains error") {
    val r = Result.error[Int](jlist(err))
    assertEquals(r.diagnostics.size, 1)
    assert(r.diagnostics.get(0).isError)
  }

  test("error from message") {
    val r = Result.error[Int]("boom")
    assert(r.isError)
    assertEquals(r.diagnostics.size, 1)
    assertEquals(r.diagnostics.get(0).message, "boom")
  }

  // -- map --

  test("map on ok transforms value") {
    val fn: java.util.function.Function[Int, Int] = x => x * 2
    assertEquals(Result.ok(42).map(fn).value, 84)
  }

  test("map on error returns error") {
    val fn: java.util.function.Function[Int, Int] = x => x * 2
    val r                                         = Result.error[Int]("fail").map(fn)
    assert(r.isError)
  }

  test("map on withWarnings preserves warnings") {
    val fn: java.util.function.Function[Int, Int] = x => x * 2
    val r                                         = Result.withWarnings(21, jlist(warn)).map(fn)
    assert(r.hasWarnings)
    assertEquals(r.value, 42)
  }

  // -- flatMap --

  test("flatMap ok -> ok") {
    val fn: java.util.function.Function[Int, Result[Int]] = x => Result.ok(x + 1)
    assertEquals(Result.ok(42).flatMap(fn).value, 43)
  }

  test("flatMap ok -> error") {
    val fn: java.util.function.Function[Int, Result[Int]] = _ => Result.error("fail")
    assert(Result.ok(42).flatMap(fn).isError)
  }

  test("flatMap error -> anything = error") {
    val fn: java.util.function.Function[Int, Result[Int]] = x => Result.ok(x + 1)
    assert(Result.error[Int]("fail").flatMap(fn).isError)
  }

  // -- conversions --

  test("roundtrip: toIor -> fromIor") {
    val r            = Result.withWarnings(42, jlist(warn))
    val roundtripped = Result.fromIor(r.toIor)
    assertEquals(roundtripped.value, 42)
    assert(roundtripped.hasWarnings)
  }

  // -- errors / warnings filters --

  test("errors filters only Error severity") {
    val r = Result.withWarnings(42, jlist(warn, err))
    assertEquals(r.errors.size, 1)
    assert(r.errors.get(0).isError)
  }

  test("warnings filters only Warning severity") {
    val r = Result.withWarnings(42, jlist(warn, err))
    assertEquals(r.warnings.size, 1)
    assert(r.warnings.get(0).isWarning)
  }

  // -- toString --

  test("toString ok") {
    assert(Result.ok(42).toString.contains("ok"))
  }

  test("toString error") {
    assert(Result.error[Int]("x").toString.contains("error"))
  }

  // -- equals --

  test("ok equals ok with same value") {
    assertEquals(Result.ok(42), Result.ok(42))
  }

  test("ok not equals different value") {
    assertNotEquals(Result.ok(42), Result.ok(43))
  }
}
