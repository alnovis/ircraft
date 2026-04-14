package io.alnovis.ircraft.core.ir

import cats.{Applicative, Eval, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.DialectInfo

/**
  * SemanticF -- the standard (core) dialect functor for ircraft.
  *
  * This is the functorized form of the original Decl ADT, following the
  * "Data Types a la Carte" approach. The type parameter `A` replaces
  * recursive references (nested declarations), enabling composition via
  * [[io.alnovis.ircraft.core.algebra.Coproduct]] and folding via
  * [[io.alnovis.ircraft.core.algebra.scheme]].
  *
  * SemanticF represents language-agnostic semantic constructs: type declarations,
  * enums, functions, type aliases, and constants. Domain-specific dialects
  * (e.g., [[io.alnovis.ircraft.dialects.proto.ProtoF]]) extend the IR by forming
  * coproducts with SemanticF.
  *
  * @tparam A the recursive carrier type, replaced by [[io.alnovis.ircraft.core.algebra.Fix]]
  *           in the fixed-point representation
  * @see [[io.alnovis.ircraft.core.algebra.Fix]]
  * @see [[io.alnovis.ircraft.core.algebra.Coproduct]]
  */
sealed trait SemanticF[+A]

/**
  * Companion object for [[SemanticF]] providing case class variants, utility
  * methods, and implicit typeclass instances.
  */
object SemanticF {

  /**
    * A type declaration node (class, struct, record, interface, etc.).
    *
    * @param name        the declared type name
    * @param kind        the kind of type (class, interface, struct, etc.)
    * @param fields      the fields (properties) of the type
    * @param functions   methods or functions declared within the type
    * @param nested      nested (inner) declarations, parameterized by the carrier type
    * @param supertypes  supertypes that this type extends or implements
    * @param typeParams  generic type parameters
    * @param visibility  access visibility (public, private, etc.)
    * @param annotations annotations attached to this type
    * @param meta        extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[TypeKind]]
    * @see [[Field]]
    * @see [[Func]]
    */
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

  /**
    * An enumeration declaration node.
    *
    * @param name        the enum name
    * @param variants    the enum variants (cases)
    * @param functions   methods declared within the enum
    * @param supertypes  supertypes that this enum extends
    * @param visibility  access visibility (public, private, etc.)
    * @param annotations annotations attached to this enum
    * @param meta        extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[EnumVariant]]
    */
  final case class EnumDeclF[+A](
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * A standalone function declaration node.
    *
    * @param func the function definition
    * @param meta extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[Func]]
    */
  final case class FuncDeclF[+A](
    func: Func,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * A type alias declaration node.
    *
    * @param name       the alias name
    * @param target     the target type expression that this alias refers to
    * @param visibility access visibility (public, private, etc.)
    * @param meta       extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[TypeExpr]]
    */
  final case class AliasDeclF[+A](
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * A constant declaration node.
    *
    * @param name       the constant name
    * @param constType  the type of the constant
    * @param value      the constant value expression
    * @param visibility access visibility (public, private, etc.)
    * @param meta       extensible metadata map
    * @tparam A the recursive carrier type
    * @see [[TypeExpr]]
    * @see [[Expr]]
    */
  final case class ConstDeclF[+A](
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * Extracts the declaration name from any [[SemanticF]] variant.
    *
    * @param fa the semantic node to extract the name from
    * @tparam A the recursive carrier type
    * @return the name of the declaration
    */
  def name[A](fa: SemanticF[A]): String = fa match {
    case TypeDeclF(n, _, _, _, _, _, _, _, _, _) => n
    case EnumDeclF(n, _, _, _, _, _, _)          => n
    case FuncDeclF(f, _)                         => f.name
    case AliasDeclF(n, _, _, _)                  => n
    case ConstDeclF(n, _, _, _, _)               => n
  }

  /**
    * Extracts the [[Meta]] from any [[SemanticF]] variant.
    *
    * @param fa the semantic node to extract metadata from
    * @tparam A the recursive carrier type
    * @return the metadata map attached to the node
    */
  def meta[A](fa: SemanticF[A]): Meta = fa match {
    case TypeDeclF(_, _, _, _, _, _, _, _, _, m) => m
    case EnumDeclF(_, _, _, _, _, _, m)          => m
    case FuncDeclF(_, m)                         => m
    case AliasDeclF(_, _, _, m)                  => m
    case ConstDeclF(_, _, _, _, m)               => m
  }

  /**
    * Returns a copy of the given [[SemanticF]] node with updated [[Meta]].
    *
    * @param fa the semantic node to update
    * @param m  the new metadata to set
    * @tparam A the recursive carrier type
    * @return a new node identical to `fa` but with metadata replaced by `m`
    */
  def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = fa match {
    case td: TypeDeclF[A @unchecked]  => td.copy(meta = m)
    case ed: EnumDeclF[A @unchecked]  => ed.copy(meta = m)
    case fd: FuncDeclF[A @unchecked]  => fd.copy(meta = m)
    case ad: AliasDeclF[A @unchecked] => ad.copy(meta = m)
    case cd: ConstDeclF[A @unchecked] => cd.copy(meta = m)
  }

  /**
    * Implicit [[cats.Traverse]] instance for [[SemanticF]].
    *
    * Only [[TypeDeclF]] has recursive children (via the `nested` field);
    * all other variants are leaves and are traversed as pure values.
    */
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

  /**
    * Implicit [[DialectInfo]] instance for [[SemanticF]], providing
    * the dialect name ("SemanticF") and operation count (5 variants).
    */
  implicit val semanticDialectInfo: DialectInfo[SemanticF] = DialectInfo("SemanticF", 5)
}
