package io.alnovis.ircraft.core

/**
  * Reference to a type in the IR.
  *
  * TypeRef is language-agnostic: it bridges proto-level types (int32, string, bytes) and language-specific types (Java
  * int/Integer, Kotlin Int/Int?). Concrete mapping happens during lowering to Code Dialects.
  */
sealed trait TypeRef

object TypeRef:

  /** Primitive scalar types common across protobuf and target languages. */
  enum PrimitiveType extends TypeRef:
    case Int32, Int64, UInt32, UInt64
    case SInt32, SInt64, Fixed32, Fixed64, SFixed32, SFixed64
    case Float32, Float64
    case Bool
    case StringType
    case Bytes

  /** Void / Unit type. */
  case object VoidType extends TypeRef

  /** Reference to a named type by fully-qualified name. */
  case class NamedType(fqn: String) extends TypeRef

  /** List/repeated type. */
  case class ListType(element: TypeRef) extends TypeRef

  /** Map type. */
  case class MapType(key: TypeRef, value: TypeRef) extends TypeRef

  /** Optional/nullable type. */
  case class OptionalType(inner: TypeRef) extends TypeRef

  /** Enum type with known values. */
  case class EnumType(fqn: String, values: List[EnumValue]) extends TypeRef

  /** Union type (for oneof-like constructs). */
  case class UnionType(alternatives: List[TypeRef]) extends TypeRef

  /** Parameterized generic type (e.g., Builder<T>). */
  case class ParameterizedType(base: TypeRef, typeArgs: List[TypeRef]) extends TypeRef

  /** Wildcard type (e.g., ? extends Foo). */
  case class WildcardType(bound: Option[TypeRef] = None) extends TypeRef

  // Common type aliases
  val STRING: TypeRef = PrimitiveType.StringType
  val BOOL: TypeRef   = PrimitiveType.Bool
  val INT: TypeRef    = PrimitiveType.Int32
  val LONG: TypeRef   = PrimitiveType.Int64
  val FLOAT: TypeRef  = PrimitiveType.Float32
  val DOUBLE: TypeRef = PrimitiveType.Float64
  val BYTES: TypeRef  = PrimitiveType.Bytes
  val VOID: TypeRef   = VoidType

  given ContentHashable[TypeRef] with

    def contentHash(a: TypeRef): Int = a match
      case p: PrimitiveType           => ContentHash.combine(1, p.ordinal)
      case VoidType                   => 2
      case NamedType(fqn)             => ContentHash.combine(3, ContentHash.ofString(fqn))
      case ListType(e)                => ContentHash.combine(4, contentHash(e))
      case MapType(k, v)              => ContentHash.combine(5, contentHash(k), contentHash(v))
      case OptionalType(i)            => ContentHash.combine(6, contentHash(i))
      case EnumType(fqn, vs)          => ContentHash.combine(7, ContentHash.ofString(fqn), ContentHash.ofList(vs))
      case UnionType(alts)            => ContentHash.combine(8, ContentHash.ofList(alts)(using this))
      case ParameterizedType(b, args) => ContentHash.combine(9, contentHash(b), ContentHash.ofList(args)(using this))
      case WildcardType(bound)        => ContentHash.combine(10, bound.map(contentHash).getOrElse(0))

/** A single enum value. */
case class EnumValue(name: String, number: Int)

object EnumValue:

  given ContentHashable[EnumValue] with

    def contentHash(a: EnumValue): Int =
      ContentHash.combine(ContentHash.ofString(a.name), a.number)
