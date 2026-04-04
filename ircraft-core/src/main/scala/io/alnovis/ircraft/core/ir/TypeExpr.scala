package io.alnovis.ircraft.core.ir

import cats.*
import cats.syntax.all.*

sealed trait TypeExpr

object TypeExpr:

  enum Primitive extends TypeExpr:
    case Bool
    case Int8, Int16, Int32, Int64
    case UInt8, UInt16, UInt32, UInt64
    case Float32, Float64
    case Char, Str
    case Bytes
    case Void
    case Any

  // -- Composite --
  case class Named(fqn: String) extends TypeExpr
  case class ListOf(elem: TypeExpr) extends TypeExpr
  case class MapOf(key: TypeExpr, value: TypeExpr) extends TypeExpr
  case class Optional(inner: TypeExpr) extends TypeExpr
  case class SetOf(elem: TypeExpr) extends TypeExpr
  case class TupleOf(elems: Vector[TypeExpr]) extends TypeExpr

  // -- Generics --
  case class Applied(base: TypeExpr, args: Vector[TypeExpr]) extends TypeExpr
  case class Wildcard(bound: Option[TypeExpr] = None) extends TypeExpr

  // -- References (resolved during pipeline) --
  case class Unresolved(sourceFqn: String) extends TypeExpr
  case class Local(name: String) extends TypeExpr
  case class Imported(path: String, name: String) extends TypeExpr

  // -- Function type --
  case class FuncType(params: Vector[TypeExpr], result: TypeExpr) extends TypeExpr

  // -- Union / Intersection --
  case class Union(alternatives: Vector[TypeExpr]) extends TypeExpr
  case class Intersection(components: Vector[TypeExpr]) extends TypeExpr

  /** Stack-safe recursive fold over a TypeExpr tree.
    * Visits the node itself and all children, combining via Monoid.
    */
  extension (t: TypeExpr)
    def foldMap[A](f: TypeExpr => A)(using M: Monoid[A]): A =
      def go(te: TypeExpr): Eval[A] = Eval.defer {
        val children: Eval[A] = te match
          case ListOf(e)          => go(e)
          case MapOf(k, v)        => (go(k), go(v)).mapN(M.combine)
          case Optional(i)        => go(i)
          case SetOf(e)           => go(e)
          case TupleOf(es)        => es.traverse(go).map(M.combineAll(_))
          case Applied(b, as)     => (go(b), as.traverse(go).map(M.combineAll(_))).mapN(M.combine)
          case FuncType(ps, r)    => (ps.traverse(go).map(M.combineAll(_)), go(r)).mapN(M.combine)
          case Union(as)          => as.traverse(go).map(M.combineAll(_))
          case Intersection(cs)   => cs.traverse(go).map(M.combineAll(_))
          case Wildcard(Some(b))  => go(b)
          case _                  => Eval.now(M.empty)
        children.map(M.combine(f(te), _))
      }
      go(t).value

  // -- Aliases --
  val STR: TypeExpr   = Primitive.Str
  val BOOL: TypeExpr  = Primitive.Bool
  val INT: TypeExpr   = Primitive.Int32
  val LONG: TypeExpr  = Primitive.Int64
  val FLOAT: TypeExpr = Primitive.Float32
  val DOUBLE: TypeExpr = Primitive.Float64
  val BYTES: TypeExpr = Primitive.Bytes
  val VOID: TypeExpr  = Primitive.Void
