package io.alnovis.ircraft.core.algebra

import cats.{ Applicative, Eval, Functor, Traverse }
import io.alnovis.ircraft.core.algebra.Algebra._
import munit.FunSuite

/** Test functors for algebra infrastructure. */
sealed trait ExprF[+A]

object ExprF {
  final case class LitF(value: Int)            extends ExprF[Nothing]
  final case class AddF[+A](left: A, right: A) extends ExprF[A]
  final case class MulF[+A](left: A, right: A) extends ExprF[A]

  implicit val functor: Functor[ExprF] = new Functor[ExprF] {
    def map[A, B](fa: ExprF[A])(f: A => B): ExprF[B] = fa match {
      case LitF(v)    => LitF(v)
      case AddF(l, r) => AddF(f(l), f(r))
      case MulF(l, r) => MulF(f(l), f(r))
    }
  }

  implicit val traverse: Traverse[ExprF] = new Traverse[ExprF] {
    def traverse[G[_], A, B](fa: ExprF[A])(f: A => G[B])(implicit G: Applicative[G]): G[ExprF[B]] = fa match {
      case LitF(v)    => G.pure(LitF(v))
      case AddF(l, r) => G.map2(f(l), f(r))(AddF(_, _))
      case MulF(l, r) => G.map2(f(l), f(r))(MulF(_, _))
    }
    def foldLeft[A, B](fa: ExprF[A], b: B)(f: (B, A) => B): B = fa match {
      case LitF(_)    => b
      case AddF(l, r) => f(f(b, l), r)
      case MulF(l, r) => f(f(b, l), r)
    }
    def foldRight[A, B](fa: ExprF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
      case LitF(_)    => lb
      case AddF(l, r) => f(l, f(r, lb))
      case MulF(l, r) => f(l, f(r, lb))
    }
  }
}

/** Second test functor for coproduct tests. */
sealed trait BoolF[+A]

object BoolF {
  final case class TrueF()                     extends BoolF[Nothing]
  final case class FalseF()                    extends BoolF[Nothing]
  final case class AndF[+A](left: A, right: A) extends BoolF[A]

  implicit val functor: Functor[BoolF] = new Functor[BoolF] {
    def map[A, B](fa: BoolF[A])(f: A => B): BoolF[B] = fa match {
      case TrueF()    => TrueF()
      case FalseF()   => FalseF()
      case AndF(l, r) => AndF(f(l), f(r))
    }
  }

  implicit val traverse: Traverse[BoolF] = new Traverse[BoolF] {
    def traverse[G[_], A, B](fa: BoolF[A])(f: A => G[B])(implicit G: Applicative[G]): G[BoolF[B]] = fa match {
      case TrueF()    => G.pure(TrueF())
      case FalseF()   => G.pure(FalseF())
      case AndF(l, r) => G.map2(f(l), f(r))(AndF(_, _))
    }
    def foldLeft[A, B](fa: BoolF[A], b: B)(f: (B, A) => B): B = fa match {
      case TrueF()    => b
      case FalseF()   => b
      case AndF(l, r) => f(f(b, l), r)
    }
    def foldRight[A, B](fa: BoolF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
      case TrueF()    => lb
      case FalseF()   => lb
      case AndF(l, r) => f(l, f(r, lb))
    }
  }
}

class FixpointSuite extends FunSuite {

  import ExprF._
  import BoolF._

  // ---- Fix ----

  test("Fix wraps and unwraps") {
    val lit = Fix[ExprF](LitF(42))
    assertEquals(lit.unfix, LitF(42))
  }

  test("Fix builds recursive trees") {
    // (1 + 2) * 3
    val tree = Fix[ExprF](
      MulF(
        Fix[ExprF](AddF(Fix[ExprF](LitF(1)), Fix[ExprF](LitF(2)))),
        Fix[ExprF](LitF(3))
      )
    )
    tree.unfix match
      case MulF(_, _) => () // ok
      case other      => fail(s"expected MulF, got $other")
  }

  // ---- cata ----

  val eval: Algebra[ExprF, Int] = {
    case LitF(v)    => v
    case AddF(l, r) => l + r
    case MulF(l, r) => l * r
  }

  test("cata evaluates arithmetic tree") {
    // (1 + 2) * 3 = 9
    val tree = Fix[ExprF](
      MulF(
        Fix[ExprF](AddF(Fix[ExprF](LitF(1)), Fix[ExprF](LitF(2)))),
        Fix[ExprF](LitF(3))
      )
    )
    assertEquals(scheme.cata(eval).apply(tree), 9)
  }

  val countNodes: Algebra[ExprF, Int] = {
    case LitF(_)    => 1
    case AddF(l, r) => 1 + l + r
    case MulF(l, r) => 1 + l + r
  }

  test("cata counts nodes") {
    // (1 + 2) * 3 = 5 nodes
    val tree = Fix[ExprF](
      MulF(
        Fix[ExprF](AddF(Fix[ExprF](LitF(1)), Fix[ExprF](LitF(2)))),
        Fix[ExprF](LitF(3))
      )
    )
    assertEquals(scheme.cata(countNodes).apply(tree), 5)
  }

  test("cata is stack-safe on deep trees") {
    // Build a left-leaning tree of depth 10000: Add(Add(Add(...Lit(0)..., Lit(1)), Lit(1)), Lit(1))
    var tree: Fix[ExprF] = Fix[ExprF](LitF(0))
    for (_ <- 0 until 10000) {
      tree = Fix[ExprF](AddF(tree, Fix[ExprF](LitF(1))))
    }
    assertEquals(scheme.cata(eval).apply(tree), 10000)
  }

  // ---- ana ----

  test("ana unfolds a value into a tree") {
    val buildLit: Coalgebra[ExprF, Int] = { n => LitF(n) }
    val tree                            = scheme.ana(buildLit).apply(42)
    assertEquals(scheme.cata(eval).apply(tree), 42)
  }

  // ---- hylo ----

  test("hylo folds without building intermediate tree") {
    // Trivial hylo: unfold into Lit, then fold back
    val unfold: Coalgebra[ExprF, Int] = { n => LitF(n) }
    val result                        = scheme.hylo(eval, unfold).apply(42)
    assertEquals(result, 42)
  }

  test("hylo with linear list functor") {
    // ListF for linear chain test
    sealed trait ListF[+A]
    case class ConsF[+A](head: Int, tail: A) extends ListF[A]
    case class NilF()                        extends ListF[Nothing]

    implicit val listTraverse: Traverse[ListF] = new Traverse[ListF] {
      def traverse[G[_], A, B](fa: ListF[A])(f: A => G[B])(implicit G: Applicative[G]): G[ListF[B]] = fa match {
        case ConsF(h, t) => G.map(f(t))(ConsF(h, _))
        case NilF()      => G.pure(NilF())
      }
      def foldLeft[A, B](fa: ListF[A], b: B)(f: (B, A) => B): B = fa match {
        case ConsF(_, t) => f(b, t)
        case NilF()      => b
      }
      def foldRight[A, B](fa: ListF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
        case ConsF(_, t) => f(t, lb)
        case NilF()      => lb
      }
    }

    // Sum 1..100 = 5050 via hylo
    val unfold: Coalgebra[ListF, Int] = { n =>
      if (n <= 0) NilF()
      else ConsF(n, n - 1)
    }
    val fold: Algebra[ListF, Int] = {
      case ConsF(h, acc) => h + acc
      case NilF()        => 0
    }
    assertEquals(scheme.hylo(fold, unfold).apply(100), 5050)
  }

  // ---- Coproduct ----

  test("Coproduct.Inl wraps left functor") {
    val cp: Coproduct[ExprF, BoolF, Fix[ExprF]] = Coproduct.Inl(LitF(42))
    cp match
      case Coproduct.Inl(LitF(42)) => () // ok
      case other                   => fail(s"expected Inl(LitF(42)), got $other")
  }

  test("Coproduct.Inr wraps right functor") {
    val cp: Coproduct[ExprF, BoolF, Nothing] = Coproduct.Inr(TrueF())
    cp match
      case Coproduct.Inr(TrueF()) => () // ok
      case other                  => fail(s"expected Inr(TrueF()), got $other")
  }

  test("Coproduct Functor maps over both sides") {
    val f                            = Functor[ExprF :+: BoolF]
    val left: (ExprF :+: BoolF)[Int] = Coproduct.Inl(AddF(1, 2))
    val mapped                       = f.map(left)(_ * 10)
    assertEquals(mapped, Coproduct.Inl(AddF(10, 20)))
  }

  // ---- Inject ----

  test("Inject refl injects identity") {
    val inj             = Inject[ExprF, ExprF]
    val lit: ExprF[Int] = LitF(42)
    assertEquals(inj.inj(lit), lit)
    assertEquals(inj.prj(lit), Some(lit))
  }

  test("Inject left injects into coproduct left") {
    val inj             = Inject[ExprF, ExprF :+: BoolF]
    val lit: ExprF[Int] = LitF(42)
    val injected        = inj.inj(lit)
    assertEquals(injected, Coproduct.Inl(lit))
    assertEquals(inj.prj(injected), Some(lit))
  }

  test("Inject right injects into coproduct right") {
    val inj           = Inject[BoolF, ExprF :+: BoolF]
    val t: BoolF[Int] = TrueF()
    val injected      = inj.inj(t)
    assertEquals(injected, Coproduct.Inr(t))
    assertEquals(inj.prj(injected), Some(t))
  }

  test("Inject prj returns None for wrong side") {
    val injExpr                         = Inject[ExprF, ExprF :+: BoolF]
    val injBool                         = Inject[BoolF, ExprF :+: BoolF]
    val boolVal: (ExprF :+: BoolF)[Int] = Coproduct.Inr(TrueF())
    assertEquals(injExpr.prj(boolVal), None)
    assertEquals(injBool.prj(boolVal), Some(TrueF()))
  }

  // ---- eliminate ----

  test("eliminate removes dialect from coproduct") {
    val injExpr = Inject[ExprF, BoolF :+: ExprF]
    val injBool = Inject[BoolF, BoolF :+: ExprF]

    // Build: And(True, False)
    val mixed: Fix[BoolF :+: ExprF] = Fix(
      injBool.inj(
        AndF(
          Fix(injBool.inj(TrueF())),
          Fix(injBool.inj(FalseF()))
        )
      )
    )

    // Algebra: BoolF -> ExprF (True=1, False=0, And=Mul)
    val boolToExpr: Algebra[BoolF, Fix[ExprF]] = {
      case TrueF()    => Fix[ExprF](LitF(1))
      case FalseF()   => Fix[ExprF](LitF(0))
      case AndF(l, r) => Fix[ExprF](MulF(l, r))
    }

    val result: Fix[ExprF] = eliminate.dialect(boolToExpr).apply(mixed)
    // And(True, False) -> Mul(Lit(1), Lit(0)) -> 0
    assertEquals(scheme.cata(eval).apply(result), 0)
  }

  test("eliminate preserves target dialect nodes") {
    val injExpr = Inject[ExprF, BoolF :+: ExprF]
    val tree: Fix[BoolF :+: ExprF] = Fix(
      injExpr.inj(
        AddF(
          Fix(injExpr.inj(LitF(10))),
          Fix(injExpr.inj(LitF(20)))
        )
      )
    )

    val boolToExpr: Algebra[BoolF, Fix[ExprF]] = {
      case TrueF()    => Fix[ExprF](LitF(1))
      case FalseF()   => Fix[ExprF](LitF(0))
      case AndF(l, r) => Fix[ExprF](MulF(l, r))
    }

    val result: Fix[ExprF] = eliminate.dialect(boolToExpr).apply(tree)
    assertEquals(scheme.cata(eval).apply(result), 30)
  }
}
