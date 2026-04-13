package io.alnovis.ircraft.core.algebra

import munit.FunSuite

class DialectInfoSuite extends FunSuite {

  // Step A: basic trait + factory
  sealed trait DummyF[+A]
  implicit val dummyInfo: DialectInfo[DummyF] = DialectInfo("Dummy", 2)

  test("DialectInfo factory creates instance with correct name") {
    assertEquals(DialectInfo[DummyF].dialectName, "Dummy")
  }

  test("DialectInfo factory creates instance with correct operationCount") {
    assertEquals(DialectInfo[DummyF].operationCount, 2)
  }

  // Step B: SemanticF instance
  test("SemanticF has DialectInfo with correct name") {
    import io.alnovis.ircraft.core.ir.SemanticF
    assertEquals(DialectInfo[SemanticF].dialectName, "SemanticF")
  }

  test("SemanticF has DialectInfo with operationCount 5") {
    import io.alnovis.ircraft.core.ir.SemanticF
    assertEquals(DialectInfo[SemanticF].operationCount, 5)
  }
}
