package io.alnovis.ircraft.emitters.java

import cats.kernel.instances.set.*
import io.alnovis.ircraft.core.ir.TypeExpr
import io.alnovis.ircraft.core.ir.TypeExpr.*
import io.alnovis.ircraft.emit.TypeMapping

object JavaTypeMapping extends TypeMapping:

  def typeName(t: TypeExpr): String = t match
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

  override def boxedName(t: TypeExpr): String = t match
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

  def imports(t: TypeExpr): Set[String] =
    t.foldMap {
      case Named(fqn) if fqn.contains(".") => Set(fqn)
      case Imported(path, _)               => Set(path)
      case _: ListOf                       => Set("java.util.List")
      case _: MapOf                        => Set("java.util.Map")
      case _: SetOf                        => Set("java.util.Set")
      case _                               => Set.empty
    }
