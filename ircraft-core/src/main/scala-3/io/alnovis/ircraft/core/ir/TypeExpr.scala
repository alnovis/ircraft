package io.alnovis.ircraft.core.ir

import cats.*
import cats.syntax.all.*

/**
  * Algebraic data type representing type expressions in the ircraft IR.
  *
  * A [[TypeExpr]] is a tree structure that describes a type reference appearing
  * in field types, function parameters, return types, supertypes, etc.
  * It covers primitives, composites (lists, maps, optionals), generics,
  * function types, union/intersection types, and resolution-stage references.
  *
  * The resolution lifecycle for type references is:
  *   1. [[TypeExpr.Unresolved]] -- freshly parsed, not yet resolved
  *   2. [[TypeExpr.Local]] -- resolved to a type within the same compilation unit
  *   3. [[TypeExpr.Imported]] -- resolved to a type from another compilation unit
  *   4. [[TypeExpr.Named]] -- fully qualified name after all resolution passes
  *
  * @see [[TypeExpr.Primitive]] for the built-in primitive type enumeration
  * @see [[TypeExpr.foldMap]] for stack-safe recursive traversal
  */
sealed trait TypeExpr

/**
  * Companion object for [[TypeExpr]] containing all concrete subtypes, convenience aliases
  * for common primitives, and a stack-safe recursive `foldMap` extension method.
  */
object TypeExpr:

  /**
    * Enumeration of built-in primitive types.
    *
    * These represent language-agnostic primitive types that every target language
    * is expected to support (with appropriate mapping).
    */
  enum Primitive extends TypeExpr:
    /** Boolean type. */
    case Bool

    /** Signed 8-bit integer. */
    case Int8

    /** Signed 16-bit integer. */
    case Int16

    /** Signed 32-bit integer. */
    case Int32

    /** Signed 64-bit integer. */
    case Int64

    /** Unsigned 8-bit integer. */
    case UInt8

    /** Unsigned 16-bit integer. */
    case UInt16

    /** Unsigned 32-bit integer. */
    case UInt32

    /** Unsigned 64-bit integer. */
    case UInt64

    /** 32-bit IEEE 754 floating point. */
    case Float32

    /** 64-bit IEEE 754 floating point. */
    case Float64

    /** Single Unicode character. */
    case Char

    /** String (UTF-8 text). */
    case Str

    /** Raw byte sequence. */
    case Bytes

    /** Void / unit type (no value). */
    case Void

    /** Top type (any value). */
    case Any

  // -- Composite --

  /**
    * A named (fully qualified) type reference.
    *
    * @param fqn the fully qualified name of the referenced type (e.g., `"com.example.Foo"`)
    */
  case class Named(fqn: String) extends TypeExpr

  /**
    * An ordered collection (list/array) of elements.
    *
    * @param elem the element type
    */
  case class ListOf(elem: TypeExpr) extends TypeExpr

  /**
    * A key-value mapping type.
    *
    * @param key   the key type
    * @param value the value type
    */
  case class MapOf(key: TypeExpr, value: TypeExpr) extends TypeExpr

  /**
    * An optional (nullable) type wrapper.
    *
    * @param inner the underlying type that may be absent
    */
  case class Optional(inner: TypeExpr) extends TypeExpr

  /**
    * An unordered collection of unique elements.
    *
    * @param elem the element type
    */
  case class SetOf(elem: TypeExpr) extends TypeExpr

  /**
    * A fixed-size product of heterogeneous types.
    *
    * @param elems the tuple component types, in order
    */
  case class TupleOf(elems: Vector[TypeExpr]) extends TypeExpr

  // -- Generics --

  /**
    * A generic type application (e.g., `Foo[Bar, Baz]`).
    *
    * @param base the base (unapplied) type
    * @param args the type arguments applied to `base`
    */
  case class Applied(base: TypeExpr, args: Vector[TypeExpr]) extends TypeExpr

  /**
    * A wildcard type, optionally bounded (e.g., `? extends Foo`).
    *
    * @param bound an optional upper bound for the wildcard
    */
  case class Wildcard(bound: Option[TypeExpr] = None) extends TypeExpr

  // -- References (resolved during pipeline) --

  /**
    * An unresolved type reference, as parsed from the source.
    *
    * This is the initial state before type resolution passes run.
    * After resolution, it should be replaced by [[Local]], [[Imported]], or [[Named]].
    *
    * @param sourceFqn the original fully qualified name from the source
    * @see [[io.alnovis.ircraft.core.Passes.validateResolved]] for the validation pass that rejects remaining unresolved types
    */
  case class Unresolved(sourceFqn: String) extends TypeExpr

  /**
    * A type reference resolved to a declaration in the same compilation unit.
    *
    * @param name the simple name of the locally-defined type
    */
  case class Local(name: String) extends TypeExpr

  /**
    * A type reference resolved to a declaration in another compilation unit, requiring an import.
    *
    * @param path the import path (package or module path)
    * @param name the simple name of the imported type
    */
  case class Imported(path: String, name: String) extends TypeExpr

  // -- Function type --

  /**
    * A function type (e.g., `(Int, String) -> Bool`).
    *
    * @param params the parameter types
    * @param result the return type
    */
  case class FuncType(params: Vector[TypeExpr], result: TypeExpr) extends TypeExpr

  // -- Union / Intersection --

  /**
    * A union type (e.g., `A | B | C`).
    *
    * @param alternatives the member types of the union
    */
  case class Union(alternatives: Vector[TypeExpr]) extends TypeExpr

  /**
    * An intersection type (e.g., `A & B & C`).
    *
    * @param components the member types of the intersection
    */
  case class Intersection(components: Vector[TypeExpr]) extends TypeExpr

  /**
    * Stack-safe recursive fold over a [[TypeExpr]] tree.
    *
    * Visits the node itself and all of its children, combining results
    * via the given `Monoid`. Uses `cats.Eval` for trampolining to ensure
    * stack safety on deeply nested type trees.
    *
    * {{{
    * // Collect all Unresolved type FQNs:
    * val unresolved: Vector[String] = expr.foldMap {
    *   case TypeExpr.Unresolved(fqn) => Vector(fqn)
    *   case _                        => Vector.empty
    * }
    * }}}
    */
  extension (t: TypeExpr)

    /**
      * Folds over this [[TypeExpr]] and all nested children, combining results via `Monoid`.
      *
      * @tparam A the result type (must have a `Monoid` instance)
      * @param f a function applied to each [[TypeExpr]] node
      * @return the monoidal combination of all results
      */
    def foldMap[A](f: TypeExpr => A)(using M: Monoid[A]): A =
      def go(te: TypeExpr): Eval[A] = Eval.defer {
        val children: Eval[A] = te match
          case ListOf(e)         => go(e)
          case MapOf(k, v)       => (go(k), go(v)).mapN(M.combine)
          case Optional(i)       => go(i)
          case SetOf(e)          => go(e)
          case TupleOf(es)       => es.traverse(go).map(M.combineAll(_))
          case Applied(b, as)    => (go(b), as.traverse(go).map(M.combineAll(_))).mapN(M.combine)
          case FuncType(ps, r)   => (ps.traverse(go).map(M.combineAll(_)), go(r)).mapN(M.combine)
          case Union(as)         => as.traverse(go).map(M.combineAll(_))
          case Intersection(cs)  => cs.traverse(go).map(M.combineAll(_))
          case Wildcard(Some(b)) => go(b)
          case _                 => Eval.now(M.empty)
        children.map(M.combine(f(te), _))
      }
      go(t).value

  // -- Aliases --
  /** Convenience alias for [[Primitive.Str]]. */
  val STR: TypeExpr = Primitive.Str

  /** Convenience alias for [[Primitive.Bool]]. */
  val BOOL: TypeExpr = Primitive.Bool

  /** Convenience alias for [[Primitive.Int32]]. */
  val INT: TypeExpr = Primitive.Int32

  /** Convenience alias for [[Primitive.Int64]]. */
  val LONG: TypeExpr = Primitive.Int64

  /** Convenience alias for [[Primitive.Float32]]. */
  val FLOAT: TypeExpr = Primitive.Float32

  /** Convenience alias for [[Primitive.Float64]]. */
  val DOUBLE: TypeExpr = Primitive.Float64

  /** Convenience alias for [[Primitive.Bytes]]. */
  val BYTES: TypeExpr = Primitive.Bytes

  /** Convenience alias for [[Primitive.Void]]. */
  val VOID: TypeExpr = Primitive.Void
