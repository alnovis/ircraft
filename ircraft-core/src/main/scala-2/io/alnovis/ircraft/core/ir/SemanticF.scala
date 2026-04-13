package io.alnovis.ircraft.core.ir

import cats.{Applicative, Eval, Functor, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.DialectInfo

/** SemanticF -- the standard dialect functor for ircraft.
  *
  * This is the functorized form of the original Decl ADT.
  * The type parameter A replaces recursive references (nested declarations).
  */
sealed trait SemanticF[+A]

object SemanticF {

  final case class TypeDeclF[+A](
    name: String,
    kind: TypeKind,
    fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    typeParams: Vector[TypeParam] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  final case class EnumDeclF[+A](
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  final case class FuncDeclF[+A](
    func: Func,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  final case class AliasDeclF[+A](
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  final case class ConstDeclF[+A](
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  def name[A](fa: SemanticF[A]): String = fa match {
    case TypeDeclF(n, _, _, _, _, _, _, _, _, _) => n
    case EnumDeclF(n, _, _, _, _, _, _)          => n
    case FuncDeclF(f, _)                         => f.name
    case AliasDeclF(n, _, _, _)                  => n
    case ConstDeclF(n, _, _, _, _)               => n
  }

  def meta[A](fa: SemanticF[A]): Meta = fa match {
    case TypeDeclF(_, _, _, _, _, _, _, _, _, m) => m
    case EnumDeclF(_, _, _, _, _, _, m)          => m
    case FuncDeclF(_, m)                         => m
    case AliasDeclF(_, _, _, m)                  => m
    case ConstDeclF(_, _, _, _, m)               => m
  }

  def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = fa match {
    case td: TypeDeclF[A @unchecked]  => td.copy(meta = m)
    case ed: EnumDeclF[A @unchecked]  => ed.copy(meta = m)
    case fd: FuncDeclF[A @unchecked]  => fd.copy(meta = m)
    case ad: AliasDeclF[A @unchecked] => ad.copy(meta = m)
    case cd: ConstDeclF[A @unchecked] => cd.copy(meta = m)
  }

  implicit val semanticFunctor: Functor[SemanticF] = new Functor[SemanticF] {
    def map[A, B](fa: SemanticF[A])(f: A => B): SemanticF[B] = fa match {
      case TypeDeclF(n, k, flds, fns, nested, st, tp, v, ann, m) =>
        TypeDeclF(n, k, flds, fns, nested.map(f), st, tp, v, ann, m)
      case EnumDeclF(n, vs, fns, st, v, ann, m) =>
        EnumDeclF(n, vs, fns, st, v, ann, m)
      case FuncDeclF(fn, m)          => FuncDeclF(fn, m)
      case AliasDeclF(n, t, v, m)    => AliasDeclF(n, t, v, m)
      case ConstDeclF(n, t, e, v, m) => ConstDeclF(n, t, e, v, m)
    }
  }

  implicit val semanticTraverse: Traverse[SemanticF] = new Traverse[SemanticF] {
    def traverse[G[_], A, B](fa: SemanticF[A])(f: A => G[B])(implicit G: Applicative[G]): G[SemanticF[B]] = fa match {
      case TypeDeclF(n, k, flds, fns, nested, st, tp, v, ann, m) =>
        nested.traverse(f).map(ns => TypeDeclF[B](n, k, flds, fns, ns, st, tp, v, ann, m))
      case EnumDeclF(n, vs, fns, st, v, ann, m) =>
        G.pure(EnumDeclF[B](n, vs, fns, st, v, ann, m))
      case FuncDeclF(fn, m) =>
        G.pure(FuncDeclF[B](fn, m))
      case AliasDeclF(n, t, v, m) =>
        G.pure(AliasDeclF[B](n, t, v, m))
      case ConstDeclF(n, t, e, v, m) =>
        G.pure(ConstDeclF[B](n, t, e, v, m))
    }

    def foldLeft[A, B](fa: SemanticF[A], b: B)(f: (B, A) => B): B = fa match {
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested.foldLeft(b)(f)
      case _ => b
    }

    def foldRight[A, B](fa: SemanticF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match {
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) =>
        Traverse[Vector].foldRight(nested, lb)(f)
      case _ => lb
    }
  }

  implicit val semanticDialectInfo: DialectInfo[SemanticF] = DialectInfo("SemanticF", 5)
}
