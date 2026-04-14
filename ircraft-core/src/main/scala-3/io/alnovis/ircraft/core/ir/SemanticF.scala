package io.alnovis.ircraft.core.ir

import cats.{ Applicative, Eval, Functor, Traverse }
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.DialectInfo

/**
  * SemanticF -- the standard (core) dialect functor for ircraft.
  *
  * This is the functorized form of the original Decl ADT, following the
  * "Data Types a la Carte" approach. The type parameter `A` replaces recursive
  * references (nested declarations), enabling composition via [[io.alnovis.ircraft.core.algebra.Coproduct]]
  * and stack-safe recursion via [[io.alnovis.ircraft.core.algebra.Fix]] and [[io.alnovis.ircraft.core.algebra.scheme]].
  *
  * All non-recursive children ([[Field]], [[Func]], [[Expr]], [[TypeExpr]]) remain concrete.
  *
  * The companion object provides:
  *   - Accessor functions (`name`, `meta`, `withMeta`) that work uniformly across all variants.
  *   - A `cats.Traverse` instance for bottom-up and top-down IR transformations.
  *   - A [[DialectInfo]] instance identifying this dialect.
  *
  * @tparam A the recursive position type (typically [[io.alnovis.ircraft.core.algebra.Fix]][SemanticF]
  *           or [[io.alnovis.ircraft.core.algebra.Fix]][SemanticF :+: D] in a composed IR)
  * @see [[io.alnovis.ircraft.core.algebra.Fix]] for the fixpoint wrapper
  * @see [[io.alnovis.ircraft.core.algebra.Coproduct]] for dialect composition
  * @see [[io.alnovis.ircraft.core.algebra.scheme]] for recursion schemes
  */
enum SemanticF[+A]:

  /**
    * A type declaration: class, interface, struct, record, etc.
    *
    * This is the most general declaration form. The [[TypeKind]] discriminator
    * determines the specific kind of type being declared.
    *
    * @param name        the declaration name
    * @param kind        the kind of type (class, interface, struct, etc.)
    * @param fields      the fields (properties) of the type
    * @param functions   the methods/functions declared within the type
    * @param nested      nested (child) declarations at the recursive position
    * @param supertypes  supertypes (extends/implements) as type expressions
    * @param typeParams  generic type parameters
    * @param visibility  access level (public, private, etc.)
    * @param annotations annotations attached to the type
    * @param meta        untyped metadata map for pipeline-specific information
    */
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

  /**
    * An enumeration declaration with named variants and optional methods.
    *
    * @param name        the enum name
    * @param variants    the enum variants (cases)
    * @param functions   methods declared within the enum
    * @param supertypes  supertypes the enum extends/implements
    * @param visibility  access level
    * @param annotations annotations attached to the enum
    * @param meta        metadata map
    */
  case EnumDeclF(
    name: String,
    variants: Vector[EnumVariant] = Vector.empty,
    functions: Vector[Func] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty,
    visibility: Visibility = Visibility.Public,
    annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * A standalone function declaration (not part of a type).
    *
    * @param func the function definition
    * @param meta metadata map
    */
  case FuncDeclF(
    func: Func,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * A type alias declaration (e.g., `type Foo = Bar`).
    *
    * @param name       the alias name
    * @param target     the target type expression the alias refers to
    * @param visibility access level
    * @param meta       metadata map
    */
  case AliasDeclF(
    name: String,
    target: TypeExpr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

  /**
    * A constant declaration (e.g., `val X = 42`).
    *
    * @param name       the constant name
    * @param constType  the type of the constant
    * @param value      the constant value expression
    * @param visibility access level
    * @param meta       metadata map
    */
  case ConstDeclF(
    name: String,
    constType: TypeExpr,
    value: Expr,
    visibility: Visibility = Visibility.Public,
    meta: Meta = Meta.empty
  ) extends SemanticF[A]

/**
  * Companion object for [[SemanticF]] providing uniform accessors, a `cats.Traverse` instance,
  * and a [[DialectInfo]] instance.
  */
object SemanticF:

  /**
    * Extracts the name from any [[SemanticF]] variant.
    *
    * @tparam A the recursive position type
    * @param fa the semantic functor node
    * @return the declaration name
    */
  def name[A](fa: SemanticF[A]): String = fa match
    case TypeDeclF(n, _, _, _, _, _, _, _, _, _) => n
    case EnumDeclF(n, _, _, _, _, _, _)          => n
    case FuncDeclF(f, _)                         => f.name
    case AliasDeclF(n, _, _, _)                  => n
    case ConstDeclF(n, _, _, _, _)               => n

  /**
    * Extracts the [[Meta]] from any [[SemanticF]] variant.
    *
    * @tparam A the recursive position type
    * @param fa the semantic functor node
    * @return the metadata map
    */
  def meta[A](fa: SemanticF[A]): Meta = fa match
    case TypeDeclF(_, _, _, _, _, _, _, _, _, m) => m
    case EnumDeclF(_, _, _, _, _, _, m)          => m
    case FuncDeclF(_, m)                         => m
    case AliasDeclF(_, _, _, m)                  => m
    case ConstDeclF(_, _, _, _, m)               => m

  /**
    * Returns a copy of the given node with an updated [[Meta]].
    *
    * @tparam A the recursive position type
    * @param fa the semantic functor node
    * @param m  the new metadata to set
    * @return a copy of `fa` with `meta` replaced by `m`
    */
  def withMeta[A](fa: SemanticF[A], m: Meta): SemanticF[A] = fa match
    case td: TypeDeclF[A]  => td.copy(meta = m)
    case ed: EnumDeclF[A]  => ed.copy(meta = m)
    case fd: FuncDeclF[A]  => fd.copy(meta = m)
    case ad: AliasDeclF[A] => ad.copy(meta = m)
    case cd: ConstDeclF[A] => cd.copy(meta = m)

  // Traverse extends Functor, so no separate Functor instance needed
  /**
    * `cats.Traverse` instance for [[SemanticF]].
    *
    * Only the [[TypeDeclF]] variant contains recursive children (via `nested`);
    * all other variants are leaves and are lifted into the applicative unchanged.
    */
  given Traverse[SemanticF] with

    /**
      * Maps each recursive child through an effectful function `f` and reassembles the node.
      *
      * @tparam G the applicative effect type
      * @tparam A the input recursive position type
      * @tparam B the output recursive position type
      * @param fa the semantic functor node to traverse
      * @param f  the effectful transformation to apply to each recursive child
      * @return the transformed node wrapped in `G`
      */
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

    /**
      * Left-associative fold over recursive children.
      *
      * @tparam A the recursive child type
      * @tparam B the accumulator type
      * @param fa the node to fold over
      * @param b  the initial accumulator value
      * @param f  the combining function
      * @return the final accumulated value
      */
    def foldLeft[A, B](fa: SemanticF[A], b: B)(f: (B, A) => B): B = fa match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested.foldLeft(b)(f)
      case _                                            => b

    /**
      * Right-associative lazy fold over recursive children.
      *
      * @tparam A the recursive child type
      * @tparam B the result type
      * @param fa the node to fold over
      * @param lb the lazy initial value
      * @param f  the combining function
      * @return the lazily computed result
      */
    def foldRight[A, B](fa: SemanticF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => nested.foldRight(lb)(f)
      case _                                            => lb

  /** [[DialectInfo]] instance identifying SemanticF with 5 operations. */
  given DialectInfo[SemanticF] = DialectInfo("SemanticF", 5)
