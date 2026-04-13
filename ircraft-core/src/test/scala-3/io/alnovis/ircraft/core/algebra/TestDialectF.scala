package io.alnovis.ircraft.core.algebra

import cats.{ Applicative, Eval, Functor, Traverse }
import cats.syntax.all._

/**
  * Test dialect functor demonstrating the template-based approach.
  *
  * 4 cases covering all recursive field patterns:
  *   - LeafF: data-only (no A)
  *   - BranchF: Vector[A] (collection of recursive)
  *   - PairF: two direct A fields
  *   - OptionalF: Option[A] (optional recursive)
  */
enum TestDialectF[+A]:
  case LeafF(value: String)
  case BranchF(label: String, children: Vector[A])
  case PairF(left: A, right: A)
  case OptionalF(name: String, child: Option[A])

object TestDialectF:

  import TestDialectF._

  // ---- Template Rule 1: Functor ----
  // For each case:
  //   data field   -> copy as-is
  //   A field      -> f(x)
  //   Vector[A]    -> vec.map(f)
  //   Option[A]    -> opt.map(f)

  given Functor[TestDialectF] with

    def map[A, B](fa: TestDialectF[A])(f: A => B): TestDialectF[B] = fa match
      case LeafF(v)             => LeafF(v)
      case BranchF(l, children) => BranchF(l, children.map(f))
      case PairF(left, right)   => PairF(f(left), f(right))
      case OptionalF(n, child)  => OptionalF(n, child.map(f))

  // ---- Template Rule 2: Traverse ----
  // traverse: data-only -> pure(copy), A -> f(x), Vector[A] -> vec.traverse(f), Option[A] -> opt.traverse(f)
  // Then reconstruct with .map / .mapN
  // foldLeft:  data-only -> b, A -> f(b,x), Vector[A] -> vec.foldLeft(b)(f)
  // foldRight: data-only -> lb, A -> f(x,lb), Vector[A] -> vec.foldRight(lb)(f)

  given Traverse[TestDialectF] with

    def traverse[G[_]: Applicative, A, B](fa: TestDialectF[A])(f: A => G[B]): G[TestDialectF[B]] = fa match
      case LeafF(v)             => Applicative[G].pure(LeafF(v))
      case BranchF(l, children) => children.traverse(f).map(BranchF(l, _))
      case PairF(left, right)   => (f(left), f(right)).mapN(PairF(_, _))
      case OptionalF(n, child)  => child.traverse(f).map(OptionalF(n, _))

    def foldLeft[A, B](fa: TestDialectF[A], b: B)(f: (B, A) => B): B = fa match
      case LeafF(_)             => b
      case BranchF(_, children) => children.foldLeft(b)(f)
      case PairF(left, right)   => f(f(b, left), right)
      case OptionalF(_, child)  => child.foldLeft(b)(f)

    def foldRight[A, B](fa: TestDialectF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case LeafF(_)             => lb
      case BranchF(_, children) => children.foldRight(lb)(f)
      case PairF(left, right)   => f(left, f(right, lb))
      case OptionalF(_, child)  => child.foldRight(lb)(f)

  given DialectInfo[TestDialectF] = DialectInfo("TestDialectF", 4)
