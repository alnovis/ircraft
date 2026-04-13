package io.alnovis.ircraft.core.algebra

import cats.{ Applicative, Eval, Functor, Traverse }
import io.alnovis.ircraft.core.algebra.DialectInfo
import cats.syntax.all._

/** Coproduct (disjoint union) of two functors. */
enum Coproduct[F[_], G[_], A]:
  case Inl(fa: F[A])
  case Inr(ga: G[A])

/** Right-associative type alias for Coproduct. */
type :+:[F[_], G[_]] = [A] =>> Coproduct[F, G, A]

object Coproduct:

  // Traverse extends Functor, so coproductTraverse also provides Functor[F :+: G]
  given coproductTraverse[F[_], G[_]](using F: Traverse[F], G: Traverse[G]): Traverse[F :+: G] with

    def traverse[H[_]: Applicative, A, B](fa: (F :+: G)[A])(f: A => H[B]): H[(F :+: G)[B]] = fa match
      case Coproduct.Inl(a) => F.traverse(a)(f).map(Coproduct.Inl(_))
      case Coproduct.Inr(a) => G.traverse(a)(f).map(Coproduct.Inr(_))

    def foldLeft[A, B](fa: (F :+: G)[A], b: B)(f: (B, A) => B): B = fa match
      case Coproduct.Inl(a) => F.foldLeft(a, b)(f)
      case Coproduct.Inr(a) => G.foldLeft(a, b)(f)

    def foldRight[A, B](fa: (F :+: G)[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case Coproduct.Inl(a) => F.foldRight(a, lb)(f)
      case Coproduct.Inr(a) => G.foldRight(a, lb)(f)

  given coproductDialectInfo[F[_], G[_]](using F: DialectInfo[F], G: DialectInfo[G]): DialectInfo[F :+: G] =
    DialectInfo(s"${F.dialectName} :+: ${G.dialectName}", F.operationCount + G.operationCount)
