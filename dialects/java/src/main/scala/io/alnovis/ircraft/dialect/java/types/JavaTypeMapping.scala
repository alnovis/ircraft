package io.alnovis.ircraft.dialect.java.types

import io.alnovis.ircraft.core.{ BaseLanguageTypeMapping, TypeRef }
import io.alnovis.ircraft.core.TypeRef.*

/** Maps ircraft TypeRef to Java type strings. */
object JavaTypeMapping extends BaseLanguageTypeMapping:

  protected val bytesType: String = "byte[]"
  protected val voidType: String  = "void"
  protected val unionType: String = "Object"

  protected def listType(elem: String): String = s"java.util.List<${toBoxedJavaType(elem)}>"

  protected def mapType(key: String, value: String): String =
    s"java.util.Map<${toBoxedJavaType(key)}, ${toBoxedJavaType(value)}>"
  protected def optionalType(inner: String): String = inner // Java unwraps optional

  protected def parameterizedType(base: String, args: List[String]): String =
    s"$base<${args.map(toBoxedJavaType).mkString(", ")}>"
  protected def wildcardType(bound: Option[String]): String = bound.map(b => s"? extends $b").getOrElse("?")

  /** Java primitives need boxing for generics. */
  override def toBoxedType(ref: TypeRef): String = ref match
    case PrimitiveType.Int32 | PrimitiveType.SInt32 | PrimitiveType.SFixed32 | PrimitiveType.UInt32 |
        PrimitiveType.Fixed32 =>
      "Integer"
    case PrimitiveType.Int64 | PrimitiveType.SInt64 | PrimitiveType.SFixed64 | PrimitiveType.UInt64 |
        PrimitiveType.Fixed64 =>
      "Long"
    case PrimitiveType.Float32 => "Float"
    case PrimitiveType.Float64 => "Double"
    case PrimitiveType.Bool    => "Boolean"
    case _                     => toLanguageType(ref)

  /** Java-specific: primitives in toLanguageType return lowercase (int, long, etc.). */
  override def toLanguageType(ref: TypeRef): String = ref match
    case PrimitiveType.Int32 | PrimitiveType.SInt32 | PrimitiveType.SFixed32 => "int"
    case PrimitiveType.Int64 | PrimitiveType.SInt64 | PrimitiveType.SFixed64 => "long"
    case PrimitiveType.UInt32 | PrimitiveType.Fixed32                        => "int"
    case PrimitiveType.UInt64 | PrimitiveType.Fixed64                        => "long"
    case PrimitiveType.Float32                                               => "float"
    case PrimitiveType.Float64                                               => "double"
    case PrimitiveType.Bool                                                  => "boolean"
    case _                                                                   => super.toLanguageType(ref)

  override def importsFor(ref: TypeRef): Set[String] = ref match
    case ListType(_)   => Set("java.util.List")
    case MapType(_, _) => Set("java.util.Map")
    case _             => super.importsFor(ref)

  // Helper for boxing in collection/generic contexts
  private def toBoxedJavaType(typeStr: String): String = typeStr match
    case "int"     => "Integer"
    case "long"    => "Long"
    case "float"   => "Float"
    case "double"  => "Double"
    case "boolean" => "Boolean"
    case other     => other

  // Legacy aliases
  def toJavaType(ref: TypeRef): String      = toLanguageType(ref)
  def toBoxedJavaType(ref: TypeRef): String = toBoxedType(ref)
