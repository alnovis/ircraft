package io.alnovis.ircraft.dialect.kotlin.types

import io.alnovis.ircraft.core.{ LanguageTypeMapping, TypeRef }
import io.alnovis.ircraft.core.TypeRef.*

/** Maps ircraft TypeRef to Kotlin type strings. */
object KotlinTypeMapping extends LanguageTypeMapping:

  def toLanguageType(ref: TypeRef): String = ref match
    case PrimitiveType.Int32 | PrimitiveType.SInt32 | PrimitiveType.SFixed32 => "Int"
    case PrimitiveType.Int64 | PrimitiveType.SInt64 | PrimitiveType.SFixed64 => "Long"
    case PrimitiveType.UInt32 | PrimitiveType.Fixed32                        => "Int"
    case PrimitiveType.UInt64 | PrimitiveType.Fixed64                        => "Long"
    case PrimitiveType.Float32                                               => "Float"
    case PrimitiveType.Float64                                               => "Double"
    case PrimitiveType.Bool                                                  => "Boolean"
    case PrimitiveType.StringType                                            => "String"
    case PrimitiveType.Bytes                                                 => "ByteArray"
    case VoidType                                                            => "Unit"
    case NamedType(fqn)                                                      => simpleName(fqn)
    case ListType(elem)                                                      => s"List<${toLanguageType(elem)}>"
    case MapType(k, v)                 => s"Map<${toLanguageType(k)}, ${toLanguageType(v)}>"
    case OptionalType(inner)           => s"${toLanguageType(inner)}?"
    case EnumType(fqn, _)              => simpleName(fqn)
    case UnionType(_)                  => "Any"
    case ParameterizedType(base, args) => s"${toLanguageType(base)}<${args.map(toLanguageType).mkString(", ")}>"
    case WildcardType(bound)           => bound.map(b => s"out ${toLanguageType(b)}").getOrElse("*")

  /** In Kotlin, primitives are already objects — no boxing distinction. */
  override def toBoxedType(ref: TypeRef): String = toLanguageType(ref)

  override def importsFor(ref: TypeRef): Set[String] = ref match
    case NamedType(fqn) if fqn.contains(".") => Set(fqn)
    case ParameterizedType(base, args)       => importsFor(base) ++ args.flatMap(importsFor)
    case _                                   => Set.empty
