package io.alnovis.ircraft.core.ir

import cats._
import cats.syntax.all._

sealed trait TypeExpr

object TypeExpr {

  sealed abstract class Primitive extends TypeExpr
  object Primitive {
    case object Bool extends Primitive
    case object Int8 extends Primitive
    case object Int16 extends Primitive
    case object Int32 extends Primitive
    case object Int64 extends Primitive
    case object UInt8 extends Primitive
    case object UInt16 extends Primitive
    case object UInt32 extends Primitive
    case object UInt64 extends Primitive
    case object Float32 extends Primitive
    case object Float64 extends Primitive
    case object Char extends Primitive
    case object Str extends Primitive
    case object Bytes extends Primitive
    case object Void extends Primitive
    case object Any extends Primitive
  }

  case class Named(fqn: String) extends TypeExpr
  case class ListOf(elem: TypeExpr) extends TypeExpr
  case class MapOf(key: TypeExpr, value: TypeExpr) extends TypeExpr
  case class Optional(inner: TypeExpr) extends TypeExpr
  case class SetOf(elem: TypeExpr) extends TypeExpr
  case class TupleOf(elems: Vector[TypeExpr]) extends TypeExpr
  case class Applied(base: TypeExpr, args: Vector[TypeExpr]) extends TypeExpr
  case class Wildcard(bound: Option[TypeExpr] = None) extends TypeExpr
  case class Unresolved(sourceFqn: String) extends TypeExpr
  case class Local(name: String) extends TypeExpr
  case class Imported(path: String, name: String) extends TypeExpr
  case class FuncType(params: Vector[TypeExpr], result: TypeExpr) extends TypeExpr
  case class Union(alternatives: Vector[TypeExpr]) extends TypeExpr
  case class Intersection(components: Vector[TypeExpr]) extends TypeExpr

  implicit class TypeExprOps(private val t: TypeExpr) extends AnyVal {
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

  val STR: TypeExpr = Primitive.Str
  val BOOL: TypeExpr = Primitive.Bool
  val INT: TypeExpr = Primitive.Int32
  val LONG: TypeExpr = Primitive.Int64
  val FLOAT: TypeExpr = Primitive.Float32
  val DOUBLE: TypeExpr = Primitive.Float64
  val BYTES: TypeExpr = Primitive.Bytes
  val VOID: TypeExpr = Primitive.Void
}
