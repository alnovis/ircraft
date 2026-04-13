package io.alnovis.ircraft.core.ir

import cats.{Applicative, Eval, Functor, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.DialectInfo

/** SemanticF -- the standard dialect functor for ircraft.
  *
  * This is the functorized form of the original Decl ADT.
  * The type parameter A replaces recursive references (nested declarations).
  * All non-recursive children (Field, Func, Expr, Stmt, TypeExpr) remain concrete.
  */
enum SemanticF[+A]:
  case TypeDeclF(
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

  case EnumDeclF(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  case FuncDeclF(
    func: Func,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  case AliasDeclF(
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  case ConstDeclF(
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

object SemanticF:

  /** Extract name from any SemanticF variant. */
  def name[A](fa: SemanticF[A]): String = fa match
    case TypeDeclF(n, _, _, _, _, _, _, _, _, _) => n
    case EnumDeclF(n, _, _, _, _, _, _)          => n
    case FuncDeclF(f, _)                         => f.name
    case AliasDeclF(n, _, _, _)                  => n
    case ConstDeclF(n, _, _, _, _)               => n

  /** Extract meta from any SemanticF variant. */
  def meta[A](fa: SemanticF[A]): Meta = fa match
    case TypeDeclF(_, _, _, _, _, _, _, _, _, m) => m
    case EnumDeclF(_, _, _, _, _, _, m)          => m
    case FuncDeclF(_, m)                         => m
    case AliasDeclF(_, _, _, m)                  => m
    case ConstDeclF(_, _, _, _, m)               => m

  /** Return a copy with updated meta. */
  def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = fa match
    case td: TypeDeclF[A]  => td.copy(meta = m)
    case ed: EnumDeclF[A]  => ed.copy(meta = m)
    case fd: FuncDeclF[A]  => fd.copy(meta = m)
    case ad: AliasDeclF[A] => ad.copy(meta = m)
    case cd: ConstDeclF[A] => cd.copy(meta = m)

  given Functor[SemanticF] with
    def map[A, B](fa: SemanticF[A])(f: A => B): SemanticF[B] = fa match
      case TypeDeclF(n, k, flds, fns, nested, st, tp, v, ann, m) =>
        TypeDeclF(n, k, flds, fns, nested.map(f), st, tp, v, ann, m)
      case EnumDeclF(n, vs, fns, st, v, ann, m) =>
        EnumDeclF(n, vs, fns, st, v, ann, m)
      case FuncDeclF(fn, m)       => FuncDeclF(fn, m)
      case AliasDeclF(n, t, v, m) => AliasDeclF(n, t, v, m)
      case ConstDeclF(n, t, e, v, m) => ConstDeclF(n, t, e, v, m)

  given Traverse[SemanticF] with
    def traverse[G[_]: Applicative, A, B](fa: SemanticF[A])(f: A => G[B]): G[SemanticF[B]] = fa match
      case TypeDeclF(n, k, flds, fns, nested, st, tp, v, ann, m) =>
        nested.traverse(f).map(ns => TypeDeclF(n, k, flds, fns, ns, st, tp, v, ann, m))
      case EnumDeclF(n, vs, fns, st, v, ann, m) =>
        Applicative[G].pure(EnumDeclF(n, vs, fns, st, v, ann, m))
      case FuncDeclF(fn, m) =>
        Applicative[G].pure(FuncDeclF(fn, m))
      case AliasDeclF(n, t, v, m) =>
        Applicative[G].pure(AliasDeclF(n, t, v, m))
      case ConstDeclF(n, t, e, v, m) =>
        Applicative[G].pure(ConstDeclF(n, t, e, v, m))

    def foldLeft[A, B](fa: SemanticF[A], b: B)(f: (B, A) => B): B = fa match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested.foldLeft(b)(f)
      case _ => b

    def foldRight[A, B](fa: SemanticF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested.foldRight(lb)(f)
      case _ => lb

  given DialectInfo[SemanticF] = DialectInfo("SemanticF", 5)

