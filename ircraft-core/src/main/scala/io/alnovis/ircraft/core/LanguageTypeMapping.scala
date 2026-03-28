package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.TypeRef.*

/**
  * Maps ircraft TypeRef to language-specific type strings.
  *
  * Each language dialect implements this trait to provide type name resolution, boxing (for generics), and import
  * collection.
  */
trait LanguageTypeMapping:

  /** Convert a TypeRef to the language-specific type name. */
  def toLanguageType(ref: TypeRef): String

  /** Convert a TypeRef to a boxed type (for use in generics). Defaults to toLanguageType. */
  def toBoxedType(ref: TypeRef): String = toLanguageType(ref)

  /** Extract import statements needed for a TypeRef. */
  def importsFor(ref: TypeRef): Set[String] = Set.empty

  /** Fully qualified name -> simple class name. */
  def simpleName(fqn: String): String =
    val lastDot = fqn.lastIndexOf('.')
    if lastDot >= 0 then fqn.substring(lastDot + 1) else fqn

/**
  * Base implementation sharing common type mappings across JVM languages.
  *
  * Primitive types (Int, Long, Float, Double, Boolean, String) are identical for Java, Kotlin, and Scala. Subclasses
  * override only the differing constructs: bytes, void, collections, optionals, wildcards, generics syntax.
  */
trait BaseLanguageTypeMapping extends LanguageTypeMapping:

  // ── Language-specific hooks (override in subclass) ─────────────────────

  /** Type name for byte arrays: `byte[]`, `ByteArray`, `Array[Byte]`. */
  protected def bytesType: String

  /** Type name for void/unit return: `void`, `Unit`. */
  protected def voidType: String

  /** Format a list type: `List<T>`, `List<T>`, `List[T]`. */
  protected def listType(elem: String): String

  /** Format a map type: `Map<K, V>`, `Map<K, V>`, `Map[K, V]`. */
  protected def mapType(key: String, value: String): String

  /** Format an optional type: `T` (Java), `T?` (Kotlin), `Option[T]` (Scala). */
  protected def optionalType(inner: String): String

  /** Format a parameterized type: `Base<A, B>` or `Base[A, B]`. */
  protected def parameterizedType(base: String, args: List[String]): String

  /** Format a wildcard type: `? extends T`, `out T`, `_ <: T`. */
  protected def wildcardType(bound: Option[String]): String

  /** Type name for union/unresolved: `Object`, `Any`. */
  protected def unionType: String

  // ── Common implementation ──────────────────────────────────────────────

  def toLanguageType(ref: TypeRef): String = ref match
    case PrimitiveType.Int32 | PrimitiveType.SInt32 | PrimitiveType.SFixed32 => "Int"
    case PrimitiveType.Int64 | PrimitiveType.SInt64 | PrimitiveType.SFixed64 => "Long"
    case PrimitiveType.UInt32 | PrimitiveType.Fixed32                        => "Int"
    case PrimitiveType.UInt64 | PrimitiveType.Fixed64                        => "Long"
    case PrimitiveType.Float32                                               => "Float"
    case PrimitiveType.Float64                                               => "Double"
    case PrimitiveType.Bool                                                  => "Boolean"
    case PrimitiveType.StringType                                            => "String"
    case PrimitiveType.Bytes                                                 => bytesType
    case VoidType                                                            => voidType
    case NamedType(fqn)                                                      => simpleName(fqn)
    case ListType(elem)                                                      => listType(toLanguageType(elem))
    case MapType(k, v)                 => mapType(toLanguageType(k), toLanguageType(v))
    case OptionalType(inner)           => optionalType(toLanguageType(inner))
    case EnumType(fqn, _)              => simpleName(fqn)
    case UnionType(_)                  => unionType
    case ParameterizedType(base, args) => parameterizedType(toLanguageType(base), args.map(toLanguageType))
    case WildcardType(bound)           => wildcardType(bound.map(toLanguageType))

  override def importsFor(ref: TypeRef): Set[String] = ref match
    case NamedType(fqn) if fqn.contains(".") => Set(fqn)
    case ParameterizedType(base, args)       => importsFor(base) ++ args.flatMap(importsFor)
    case _                                   => Set.empty
