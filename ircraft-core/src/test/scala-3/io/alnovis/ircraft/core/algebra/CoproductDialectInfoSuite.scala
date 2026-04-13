package io.alnovis.ircraft.core.algebra

import io.alnovis.ircraft.core.ir.SemanticF
import munit.FunSuite

class CoproductDialectInfoSuite extends FunSuite {

  // A simple test dialect
  sealed trait TestF[+A]
  given DialectInfo[TestF] = DialectInfo("TestF", 3)

  test("Coproduct DialectInfo combines names") {
    val info = DialectInfo[TestF :+: SemanticF]
    assertEquals(info.dialectName, "TestF :+: SemanticF")
  }

  test("Coproduct DialectInfo sums operationCounts") {
    val info = DialectInfo[TestF :+: SemanticF]
    assertEquals(info.operationCount, 3 + 5)
  }
}
