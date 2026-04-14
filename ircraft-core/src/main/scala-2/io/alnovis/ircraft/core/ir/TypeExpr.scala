package io.alnovis.ircraft.core.ir

import cats._
import cats.syntax.all._

/**
  * Algebraic data type representing type expressions in the ircraft IR.
  *
  * TypeExpr models the full range of type constructs found across programming
  * languages: primitives, named types, generics, collections, optionals, function
  * types, unions, intersections, and more. This is a pure data structure with
  * no platform-specific semantics.
  *
  * Resolution phases transform [[Unresolved]] references into [[Local]] or
  * [[Imported]] references during lowering.
  *
  * @see [[SemanticF]] for how type expressions are used within declarations
  * @see [[Field]] for field-level usage of type expressions
  */
sealed trait TypeExpr

/**
  * Companion object for [[TypeExpr]] providing all variant case classes,
  * convenience constants, and the [[TypeExprOps]] implicit enrichment.
  */
object TypeExpr {

  /**
    * Base class for primitive (built-in) type expressions.
    *
    * Primitive types are language-agnostic and represent the fundamental
    * scalar types available in most programming languages.
    */
  sealed abstract class Primitive extends TypeExpr

  /**
    * Companion object enumerating all primitive type variants.
    */
  object Primitive {
    /** Boolean type. */
    case object Bool extends Primitive
    /** Signed 8-bit integer. */
    case object Int8 extends Primitive
    /** Signed 16-bit integer. */
    case object Int16 extends Primitive
    /** Signed 32-bit integer. */
    case object Int32 extends Primitive
    /** Signed 64-bit integer. */
    case object Int64 extends Primitive
    /** Unsigned 8-bit integer. */
    case object UInt8 extends Primitive
    /** Unsigned 16-bit integer. */
    case object UInt16 extends Primitive
    /** Unsigned 32-bit integer. */
    case object UInt32 extends Primitive
    /** Unsigned 64-bit integer. */
    case object UInt64 extends Primitive
    /** 32-bit floating-point number. */
    case object Float32 extends Primitive
    /** 64-bit floating-point number. */
    case object Float64 extends Primitive
    /** Character type. */
    case object Char extends Primitive
    /** String type. */
    case object Str extends Primitive
    /** Raw byte sequence type. */
    case object Bytes extends Primitive
    /** Void (unit) type -- represents absence of a value. */
    case object Void extends Primitive
    /** Top type -- supertype of all types. */
    case object Any extends Primitive
  }

  /**
    * A named type reference identified by its fully qualified name.
    *
    * @param fqn the fully qualified name of the referenced type
    */
  case class Named(fqn: String) extends TypeExpr

  /**
    * A list (ordered collection) type parameterized by its element type.
    *
    * @param elem the element type expression
    */
  case class ListOf(elem: TypeExpr) extends TypeExpr

  /**
    * A map (key-value association) type parameterized by key and value types.
    *
    * @param key   the key type expression
    * @param value the value type expression
    */
  case class MapOf(key: TypeExpr, value: TypeExpr) extends TypeExpr

  /**
    * An optional (nullable) type wrapping an inner type.
    *
    * @param inner the wrapped type expression
    */
  case class Optional(inner: TypeExpr) extends TypeExpr

  /**
    * A set (unordered unique collection) type parameterized by its element type.
    *
    * @param elem the element type expression
    */
  case class SetOf(elem: TypeExpr) extends TypeExpr

  /**
    * A tuple type containing a fixed-size sequence of heterogeneous types.
    *
    * @param elems the element type expressions in order
    */
  case class TupleOf(elems: Vector[TypeExpr]) extends TypeExpr

  /**
    * A generic type application (e.g., `Foo[Bar, Baz]`).
    *
    * @param base the base type being parameterized
    * @param args the type arguments applied to the base type
    */
  case class Applied(base: TypeExpr, args: Vector[TypeExpr]) extends TypeExpr

  /**
    * A wildcard type with an optional upper bound (e.g., `? extends Foo`).
    *
    * @param bound an optional upper-bound type expression
    */
  case class Wildcard(bound: Option[TypeExpr] = None) extends TypeExpr

  /**
    * An unresolved type reference awaiting resolution by a type resolution pass.
    *
    * This variant is produced during initial IR construction and should be
    * eliminated before code generation.
    *
    * @param sourceFqn the original fully qualified name from the source schema
    * @see [[Local]]
    * @see [[Imported]]
    */
  case class Unresolved(sourceFqn: String) extends TypeExpr

  /**
    * A locally-scoped type reference (declared in the same compilation unit).
    *
    * @param name the simple (unqualified) type name
    */
  case class Local(name: String) extends TypeExpr

  /**
    * An imported type reference from another package or compilation unit.
    *
    * @param path the import path (package or module path)
    * @param name the simple type name within that path
    */
  case class Imported(path: String, name: String) extends TypeExpr

  /**
    * A function type (e.g., `(A, B) => C`).
    *
    * @param params the parameter type expressions
    * @param result the return type expression
    */
  case class FuncType(params: Vector[TypeExpr], result: TypeExpr) extends TypeExpr

  /**
    * A union type representing a value that may be one of several alternatives.
    *
    * @param alternatives the alternative type expressions
    */
  case class Union(alternatives: Vector[TypeExpr]) extends TypeExpr

  /**
    * An intersection type representing a value that satisfies all component types.
    *
    * @param components the component type expressions
    */
  case class Intersection(components: Vector[TypeExpr]) extends TypeExpr

  /**
    * Implicit value class enriching [[TypeExpr]] with recursive traversal methods.
    *
    * @param t the type expression to enrich
    */
  implicit class TypeExprOps(private val t: TypeExpr) extends AnyVal {

    /**
      * Recursively folds over this type expression and all its children,
      * accumulating results via a [[cats.Monoid]].
      *
      * The fold visits the current node first, then recurses into children.
      * Uses trampolining ([[cats.Eval]]) to ensure stack safety on deeply
      * nested types.
      *
      * @param f the function to apply to each [[TypeExpr]] node
      * @param M the monoid used to combine results
      * @tparam A the result type
      * @return the combined result of applying `f` to all nodes
      */
    def foldMap[A](f: TypeExpr => A)(implicit M: Monoid[A]): A = {
      def go(te: TypeExpr): Eval[A] = Eval.defer {
        val children: Eval[A] = te match {
          case ListOf(e)          => go(e)
          case MapOf(k, v)        => (go(k), go(v)).mapN(M.combine)
          case Optional(i)        => go(i)
          case SetOf(e)           => go(e)
          case TupleOf(es)        => es.toList.traverse(go).map(M.combineAll(_))
          case Applied(b, as)     => (go(b), as.toList.traverse(go).map(M.combineAll(_))).mapN(M.combine)
          case FuncType(ps, r)    => (ps.toList.traverse(go).map(M.combineAll(_)), go(r)).mapN(M.combine)
          case Union(as)          => as.toList.traverse(go).map(M.combineAll(_))
          case Intersection(cs)   => cs.toList.traverse(go).map(M.combineAll(_))
          case Wildcard(Some(b))  => go(b)
          case _                  => Eval.now(M.empty)
        }
        children.map(M.combine(f(te), _))
      }
      go(t).value
    }
  }

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
}
