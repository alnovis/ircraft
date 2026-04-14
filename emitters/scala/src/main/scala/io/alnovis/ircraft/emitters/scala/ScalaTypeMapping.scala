package io.alnovis.ircraft.emitters.scala

import cats.kernel.instances.set._
import io.alnovis.ircraft.core.ir.TypeExpr
import io.alnovis.ircraft.core.ir.TypeExpr._
import io.alnovis.ircraft.emit.TypeMapping

/**
  * Type mapping for Scala code emission.
  *
  * Converts IR `TypeExpr` nodes to Scala type name strings and determines the
  * required import statements. Unlike Java, Scala has no distinction between
  * primitive and boxed types, so [[boxedName]] simply delegates to [[typeName]].
  *
  * Notable mappings:
  *  - `Primitive.Int32` -> `"Int"`
  *  - `Primitive.Void` -> `"Unit"`
  *  - `ListOf(elem)` -> `"List[Elem]"`
  *  - `Optional(inner)` -> `"Option[Inner]"`
  *  - `TupleOf(elems)` -> `"(A, B, C)"` (up to 22 elements)
  *  - `FuncType(params, ret)` -> `"(A, B) => C"`
  *  - `Union(alts)` -> `"A | B"` (Scala 3 union types)
  *  - `Intersection(cs)` -> `"A & B"` (Scala 3 intersection types)
  *
  * Most common Scala types (`List`, `Map`, `Set`, `Option`) are in `scala.Predef` and
  * require no imports.
  *
  * @see [[TypeMapping]] for the base contract
  * @see [[io.alnovis.ircraft.emitters.java.JavaTypeMapping]] for the Java counterpart
  */
object ScalaTypeMapping extends TypeMapping {

  /**
    * Returns the Scala type name for the given IR type expression.
    *
    * All primitive types map to Scala's value types (`Int`, `Long`, `Boolean`, etc.).
    * Container types use Scala's bracket syntax. `Unresolved` types cause an
    * assertion error.
    *
    * @param t the IR type expression
    * @return the Scala type name string
    * @throws AssertionError if the type is `Unresolved`
    */
  def typeName(t: TypeExpr): String = t match {
    case Primitive.Bool    => "Boolean"
    case Primitive.Int8    => "Byte"
    case Primitive.Int16   => "Short"
    case Primitive.Int32   => "Int"
    case Primitive.Int64   => "Long"
    case Primitive.UInt8   => "Byte"
    case Primitive.UInt16  => "Short"
    case Primitive.UInt32  => "Int"
    case Primitive.UInt64  => "Long"
    case Primitive.Float32 => "Float"
    case Primitive.Float64 => "Double"
    case Primitive.Char    => "Char"
    case Primitive.Str     => "String"
    case Primitive.Bytes   => "Array[Byte]"
    case Primitive.Void    => "Unit"
    case Primitive.Any     => "Any"
    case Named(fqn)        => simpleName(fqn)
    case ListOf(elem)      => s"List[${typeName(elem)}]"
    case MapOf(k, v)       => s"Map[${typeName(k)}, ${typeName(v)}]"
    case Optional(inner)   => s"Option[${typeName(inner)}]"
    case SetOf(elem)       => s"Set[${typeName(elem)}]"
    case TupleOf(es) =>
      if (es.size <= 22) s"(${es.map(typeName).mkString(", ")})"
      else s"Tuple${es.size}[${es.map(typeName).mkString(", ")}]"
    case Applied(base, args) =>
      s"${typeName(base)}[${args.map(typeName).mkString(", ")}]"
    case Wildcard(None)    => "_"
    case Wildcard(Some(b)) => s"_ <: ${typeName(b)}"
    case Unresolved(fqn)   => unreachable(s"Unresolved type reached emitter: $fqn")
    case Local(name)       => name
    case Imported(_, name) => name
    case FuncType(ps, r) =>
      if (ps.isEmpty) s"() => ${typeName(r)}"
      else if (ps.size == 1) s"${typeName(ps.head)} => ${typeName(r)}"
      else s"(${ps.map(typeName).mkString(", ")}) => ${typeName(r)}"
    case Union(alts)      => alts.map(typeName).mkString(" | ")
    case Intersection(cs) => cs.map(typeName).mkString(" & ")
  }

  /**
    * Returns the boxed type name for Scala.
    *
    * Since Scala has no primitive/boxed distinction, this simply delegates
    * to [[typeName]].
    *
    * @param t the IR type expression
    * @return the Scala type name (same as [[typeName]])
    */
  override def boxedName(t: TypeExpr): String = typeName(t)

  /**
    * Returns the set of import paths required for the given type expression.
    *
    * `Named` types with a package qualifier require an import. `Imported` types
    * contribute their import path. Standard Scala collections (`List`, `Map`, `Set`,
    * `Option`) are in `scala.Predef` and require no explicit imports.
    *
    * @param t the IR type expression
    * @return the set of required import paths
    */
  def imports(t: TypeExpr): Set[String] =
    t.foldMap {
      case Named(fqn) if fqn.contains(".") => Set(fqn)
      case Imported(path, _)               => Set(path)
      // List, Map, Set, Option are in scala.Predef -- no import needed
      case _ => Set.empty
    }
}
