package io.alnovis.ircraft.core.algebra

import cats.{Applicative, Eval, Functor, Id, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.TestDialectF._
import munit.FunSuite

class TestDialectSuite extends FunSuite {

  // ---- Functor tests ----

  test("map identity law") {
    val branch: TestDialectF[Int] = BranchF("x", Vector(1, 2, 3))
    assertEquals(Functor[TestDialectF].map(branch)(identity), branch)
  }

  test("map composition law") {
    val branch: TestDialectF[Int] = BranchF("x", Vector(1, 2, 3))
    val f: Int => String = _.toString
    val g: String => Int = _.length
    val mapFG = Functor[TestDialectF].map(Functor[TestDialectF].map(branch)(f))(g)
    val mapGF = Functor[TestDialectF].map(branch)(f andThen g)
    assertEquals(mapFG, mapGF)
  }

  test("map on LeafF copies data") {
    val leaf: TestDialectF[Int] = LeafF("hello")
    assertEquals(Functor[TestDialectF].map(leaf)(_ * 2), LeafF("hello"))
  }

  test("map on BranchF transforms children") {
    val branch: TestDialectF[Int] = BranchF("root", Vector(1, 2, 3))
    assertEquals(Functor[TestDialectF].map(branch)(_ * 10), BranchF("root", Vector(10, 20, 30)))
  }

  test("map on PairF transforms both") {
    val pair: TestDialectF[Int] = PairF(5, 10)
    assertEquals(Functor[TestDialectF].map(pair)(_ + 1), PairF(6, 11))
  }

  test("map on OptionalF transforms Some") {
    assertEquals(Functor[TestDialectF].map(OptionalF("x", Some(42)))(_ * 2), OptionalF("x", Some(84)))
  }

  test("map on OptionalF preserves None") {
    assertEquals(Functor[TestDialectF].map(OptionalF[Int]("x", None))(_ * 2), OptionalF("x", None))
  }

  // ---- Traverse tests ----

  test("traverse LeafF returns pure") {
    val result = Traverse[TestDialectF].traverse(LeafF("hi"))(x => Option(x.toString))
    assertEquals(result, Some(LeafF("hi")))
  }

  test("traverse BranchF sequences children") {
    val result = Traverse[TestDialectF].traverse(BranchF("r", Vector(1, 2, 3)))(x => Option(x.toString))
    assertEquals(result, Some(BranchF("r", Vector("1", "2", "3"))))
  }

  test("traverse BranchF short-circuits on None") {
    val result = Traverse[TestDialectF].traverse(BranchF("r", Vector(1, 2, 3)))(x =>
      if (x == 2) None else Some(x.toString)
    )
    assertEquals(result, None)
  }

  test("traverse PairF sequences both") {
    val result = Traverse[TestDialectF].traverse(PairF(5, 10))(x => Option(x * 2))
    assertEquals(result, Some(PairF(10, 20)))
  }

  test("traverse OptionalF sequences Some") {
    val result = Traverse[TestDialectF].traverse(OptionalF("x", Some(42)))(x => Option(x.toString))
    assertEquals(result, Some(OptionalF("x", Some("42"))))
  }

  test("traverse OptionalF handles None child") {
    val result = Traverse[TestDialectF].traverse(OptionalF[Int]("x", None))(x => Option(x.toString))
    assertEquals(result, Some(OptionalF("x", None)))
  }

  // ---- foldLeft tests ----

  test("foldLeft on LeafF returns initial") {
    assertEquals(Traverse[TestDialectF].foldLeft(LeafF[Int]("x"), 0)(_ + _), 0)
  }

  test("foldLeft on BranchF sums children") {
    assertEquals(Traverse[TestDialectF].foldLeft(BranchF("r", Vector(1, 2, 3)), 0)(_ + _), 6)
  }

  test("foldLeft on PairF folds both") {
    assertEquals(Traverse[TestDialectF].foldLeft(PairF(5, 10), 0)(_ + _), 15)
  }

  test("foldLeft on OptionalF folds Some") {
    assertEquals(Traverse[TestDialectF].foldLeft(OptionalF("x", Some(42)), 0)(_ + _), 42)
  }

  test("foldLeft on OptionalF returns initial for None") {
    assertEquals(Traverse[TestDialectF].foldLeft(OptionalF[Int]("x", None), 0)(_ + _), 0)
  }

  // ---- foldRight tests ----

  test("foldRight on BranchF collects children in order") {
    val result = Traverse[TestDialectF].foldRight(BranchF("r", Vector(1, 2, 3)), Eval.now(Vector.empty[Int])) {
      (a, acc) => acc.map(a +: _)
    }
    assertEquals(result.value, Vector(1, 2, 3))
  }

  // ---- Integration with scheme.cata ----

  test("cata counts nodes in TestDialectF tree") {
    val tree: Fix[TestDialectF] = Fix(BranchF("root", Vector(
      Fix(LeafF("a")),
      Fix(BranchF("child", Vector(
        Fix(LeafF("b"))
      )))
    )))

    val countAlg: Algebra[TestDialectF, Int] = {
      case LeafF(_)             => 1
      case BranchF(_, children) => 1 + children.sum
      case PairF(l, r)          => 1 + l + r
      case OptionalF(_, child)  => 1 + child.getOrElse(0)
    }

    assertEquals(scheme.cata(countAlg).apply(tree), 4)
  }

  test("cata collects labels from TestDialectF tree") {
    val tree: Fix[TestDialectF] = Fix(BranchF("root", Vector(
      Fix(LeafF("x")),
      Fix(PairF(Fix(LeafF("y")), Fix(LeafF("z"))))
    )))

    val collectAlg: Algebra[TestDialectF, Vector[String]] = {
      case LeafF(v)             => Vector(v)
      case BranchF(l, children) => Vector(l) ++ children.flatten
      case PairF(l, r)          => l ++ r
      case OptionalF(n, child)  => Vector(n) ++ child.getOrElse(Vector.empty)
    }

    assertEquals(scheme.cata(collectAlg).apply(tree).toSet, Set("root", "x", "y", "z"))
  }

  // ---- Stack safety ----

  test("cata is stack-safe on deep TestDialectF tree") {
    var tree: Fix[TestDialectF] = Fix(LeafF("base"))
    for (_ <- 0 until 10000) {
      tree = Fix(BranchF("n", Vector(tree)))
    }

    val countAlg: Algebra[TestDialectF, Int] = {
      case LeafF(_)             => 1
      case BranchF(_, children) => 1 + children.sum
      case PairF(l, r)          => 1 + l + r
      case OptionalF(_, child)  => 1 + child.getOrElse(0)
    }

    assertEquals(scheme.cata(countAlg).apply(tree), 10001)
  }

  // ---- DialectInfo ----

  test("TestDialectF has correct DialectInfo") {
    assertEquals(DialectInfo[TestDialectF].dialectName, "TestDialectF")
    assertEquals(DialectInfo[TestDialectF].operationCount, 4)
  }
}
