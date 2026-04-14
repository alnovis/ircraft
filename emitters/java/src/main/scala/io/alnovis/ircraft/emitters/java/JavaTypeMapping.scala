package io.alnovis.ircraft.emitters.java

import cats.kernel.instances.set._
import io.alnovis.ircraft.core.ir.TypeExpr
import io.alnovis.ircraft.core.ir.TypeExpr._
import io.alnovis.ircraft.emit.TypeMapping

/**
  * Type mapping for Java code emission.
  *
  * Converts IR `TypeExpr` nodes to Java type name strings and determines the
  * required import statements. Handles Java's distinction between primitive types
  * (`int`, `boolean`, etc.) and their boxed counterparts (`Integer`, `Boolean`, etc.)
  * which is required when primitives appear as generic type arguments.
  *
  * Notable mappings:
  *  - `Primitive.Int32` -> `"int"` (unboxed) / `"Integer"` (boxed)
  *  - `ListOf(elem)` -> `"List<Elem>"` (requires `java.util.List` import)
  *  - `MapOf(k, v)` -> `"Map<K, V>"` (requires `java.util.Map` import)
  *  - `Optional(inner)` -> boxed inner type (no wrapper; nullable by convention)
  *  - `FuncType`, `Union`, `Intersection` -> `"Object"` (Java has no direct equivalent)
  *
  * @see [[TypeMapping]] for the base contract
  * @see [[io.alnovis.ircraft.emitters.scala.ScalaTypeMapping]] for the Scala counterpart
  */
object JavaTypeMapping extends TypeMapping {

  /**
    * Returns the Java type name for the given IR type expression.
    *
    * Primitives map to Java's unboxed types. Container types use Java generics
    * with boxed type arguments. `Unresolved` types cause an assertion error --
    * they must be resolved by earlier pipeline passes.
    *
    * @param t the IR type expression
    * @return the Java type name string
    * @throws AssertionError if the type is `Unresolved`
    */
  def typeName(t: TypeExpr): String = t match {
    case Primitive.Bool    => "boolean"
    case Primitive.Int8    => "byte"
    case Primitive.Int16   => "short"
    case Primitive.Int32   => "int"
    case Primitive.Int64   => "long"
    case Primitive.UInt8   => "byte"
    case Primitive.UInt16  => "short"
    case Primitive.UInt32  => "int"
    case Primitive.UInt64  => "long"
    case Primitive.Float32 => "float"
    case Primitive.Float64 => "double"
    case Primitive.Char    => "char"
    case Primitive.Str     => "String"
    case Primitive.Bytes   => "byte[]"
    case Primitive.Void    => "void"
    case Primitive.Any     => "Object"
    case Named(fqn)        => simpleName(fqn)
    case ListOf(elem)      => s"List<${boxedName(elem)}>"
    case MapOf(k, v)       => s"Map<${boxedName(k)}, ${boxedName(v)}>"
    case Optional(inner)   => boxedName(inner)
    case SetOf(elem)       => s"Set<${boxedName(elem)}>"
    case TupleOf(_)        => "Object[]"
    case Applied(base, args) =>
      s"${typeName(base)}<${args.map(boxedName).mkString(", ")}>"
    case Wildcard(None)    => "?"
    case Wildcard(Some(b)) => s"? extends ${typeName(b)}"
    case Unresolved(fqn) =>
      unreachable(s"Unresolved type reached emitter: $fqn. Run Passes.validateResolved before emission.")
    case Local(name)       => name
    case Imported(_, name) => name
    case FuncType(_, _)    => "Object"
    case Union(_)          => "Object"
    case Intersection(_)   => "Object"
  }

  /**
    * Returns the boxed (wrapper) type name for Java primitives.
    *
    * Maps `int` -> `"Integer"`, `boolean` -> `"Boolean"`, `char` -> `"Character"`, etc.
    * Non-primitive types delegate to [[typeName]].
    *
    * @param t the IR type expression
    * @return the boxed Java type name
    */
  override def boxedName(t: TypeExpr): String = t match {
    case Primitive.Bool    => "Boolean"
    case Primitive.Int8    => "Byte"
    case Primitive.Int16   => "Short"
    case Primitive.Int32   => "Integer"
    case Primitive.Int64   => "Long"
    case Primitive.UInt8   => "Byte"
    case Primitive.UInt16  => "Short"
    case Primitive.UInt32  => "Integer"
    case Primitive.UInt64  => "Long"
    case Primitive.Float32 => "Float"
    case Primitive.Float64 => "Double"
    case Primitive.Char    => "Character"
    case _                 => typeName(t)
  }

  /**
    * Returns the set of import paths required for the given type expression.
    *
    * Named types with a package qualifier require an import of the fully qualified name.
    * Container types require their `java.util` imports:
    *  - `ListOf` -> `java.util.List`
    *  - `MapOf` -> `java.util.Map`
    *  - `SetOf` -> `java.util.Set`
    *
    * Primitives, `String`, and other built-in types require no imports.
    *
    * @param t the IR type expression
    * @return the set of required import paths
    */
  def imports(t: TypeExpr): Set[String] =
    t.foldMap {
      case Named(fqn) if fqn.contains(".") => Set(fqn)
      case Imported(path, _)               => Set(path)
      case _: ListOf                       => Set("java.util.List")
      case _: MapOf                        => Set("java.util.Map")
      case _: SetOf                        => Set("java.util.Set")
      case _                               => Set.empty
    }
}
