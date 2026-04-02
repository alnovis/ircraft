package io.alnovis.ircraft.core.ir

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

  // -- Aliases --
  val STR: TypeExpr   = Primitive.Str
  val BOOL: TypeExpr  = Primitive.Bool
  val INT: TypeExpr   = Primitive.Int32
  val LONG: TypeExpr  = Primitive.Int64
  val FLOAT: TypeExpr = Primitive.Float32
  val DOUBLE: TypeExpr = Primitive.Float64
  val BYTES: TypeExpr = Primitive.Bytes
  val VOID: TypeExpr  = Primitive.Void
