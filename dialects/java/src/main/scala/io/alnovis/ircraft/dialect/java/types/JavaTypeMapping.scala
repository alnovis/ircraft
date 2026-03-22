package io.alnovis.ircraft.dialect.java.types

import io.alnovis.ircraft.core.TypeRef
import io.alnovis.ircraft.core.TypeRef.*

/** Maps ircraft TypeRef to Java type strings. */
object JavaTypeMapping:

  /** Returns the Java type name for a TypeRef. */
  def toJavaType(ref: TypeRef): String = ref match
    case PrimitiveType.Int32 | PrimitiveType.SInt32 | PrimitiveType.SFixed32 => "int"
    case PrimitiveType.Int64 | PrimitiveType.SInt64 | PrimitiveType.SFixed64 => "long"
    case PrimitiveType.UInt32 | PrimitiveType.Fixed32                        => "int"
    case PrimitiveType.UInt64 | PrimitiveType.Fixed64                        => "long"
    case PrimitiveType.Float32                                               => "float"
    case PrimitiveType.Float64                                               => "double"
    case PrimitiveType.Bool                                                  => "boolean"
    case PrimitiveType.StringType                                            => "String"
    case PrimitiveType.Bytes                                                 => "byte[]"
    case VoidType                                                            => "void"
    case NamedType(fqn)                                                      => simpleName(fqn)
    case ListType(elem)                => s"java.util.List<${toBoxedJavaType(elem)}>"
    case MapType(k, v)                 => s"java.util.Map<${toBoxedJavaType(k)}, ${toBoxedJavaType(v)}>"
    case OptionalType(inner)           => toJavaType(inner)
    case EnumType(fqn, _)              => simpleName(fqn)
    case UnionType(_)                  => "Object"
    case ParameterizedType(base, args) => s"${toJavaType(base)}<${args.map(toBoxedJavaType).mkString(", ")}>"
    case WildcardType(bound)           => bound.map(b => s"? extends ${toJavaType(b)}").getOrElse("?")

  /** Returns the boxed Java type (for generics). */
  def toBoxedJavaType(ref: TypeRef): String = ref match
    case PrimitiveType.Int32 | PrimitiveType.SInt32 | PrimitiveType.SFixed32 | PrimitiveType.UInt32 |
        PrimitiveType.Fixed32 =>
      "Integer"
    case PrimitiveType.Int64 | PrimitiveType.SInt64 | PrimitiveType.SFixed64 | PrimitiveType.UInt64 |
        PrimitiveType.Fixed64 =>
      "Long"
    case PrimitiveType.Float32 => "Float"
    case PrimitiveType.Float64 => "Double"
    case PrimitiveType.Bool    => "Boolean"
    case _                     => toJavaType(ref)

  /** Extract imports needed for a TypeRef. */
  def importsFor(ref: TypeRef): Set[String] = ref match
    case ListType(_)                         => Set("java.util.List")
    case MapType(_, _)                       => Set("java.util.Map")
    case NamedType(fqn) if fqn.contains(".") => Set(fqn)
    case ParameterizedType(base, args)       => importsFor(base) ++ args.flatMap(importsFor)
    case _                                   => Set.empty

  /** Fully qualified name → simple class name. */
  def simpleName(fqn: String): String =
    val lastDot = fqn.lastIndexOf('.')
    if lastDot >= 0 then fqn.substring(lastDot + 1) else fqn
