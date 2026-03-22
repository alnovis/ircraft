package io.alnovis.ircraft.dialect.proto.types

/**
  * Type of conflict between field types across proto versions.
  *
  * Maps directly to proto-wrapper-plugin's MergedField.ConflictType.
  */
enum ConflictType(val handling: ConflictHandling, val description: String):

  /** No type conflict — same type in all versions. */
  case None extends ConflictType(ConflictHandling.Native, "Types are identical")

  /** int ↔ enum conflict (uses int with enum helper methods). */
  case IntEnum extends ConflictType(ConflictHandling.Converted, "Uses int type with enum helper methods")

  /** enum ↔ enum conflict (different enum types, uses int). */
  case EnumEnum extends ConflictType(ConflictHandling.Converted, "Uses int type for unified access")

  /** Integer type widening: int32 → int64 (uses long). */
  case Widening extends ConflictType(ConflictHandling.Converted, "Uses wider type (long)")

  /** Float type widening: float → double (uses double). */
  case FloatDouble extends ConflictType(ConflictHandling.Converted, "Uses double type")

  /** Signed/unsigned conflict: int32 ↔ uint32 (uses long for safety). */
  case SignedUnsigned extends ConflictType(ConflictHandling.Converted, "Uses long type for unsigned safety")

  /** Repeated ↔ singular conflict (uses List for both). */
  case RepeatedSingle extends ConflictType(ConflictHandling.Converted, "Uses List<T> for unified access")

  /** Type narrowing: long → int (potential data loss). */
  case Narrowing extends ConflictType(ConflictHandling.Warning, "Potential data loss on narrowing conversion")

  /** string ↔ bytes conflict (requires manual conversion). */
  case StringBytes extends ConflictType(ConflictHandling.Manual, "Requires getBytes()/new String() conversion")

  /** Primitive to message conflict (generates dual accessors). */
  case PrimitiveMessage
      extends ConflictType(ConflictHandling.Converted, "Generates getXxx() and getXxxMessage() accessors")

  /** Optional ↔ required conflict (handled via hasX() methods). */
  case OptionalRequired extends ConflictType(ConflictHandling.Native, "Provides hasX() method for checking")

  /** Other incompatible types (not convertible). */
  case Incompatible extends ConflictType(ConflictHandling.Incompatible, "Incompatible type change")

/** How the plugin handles a type conflict. */
enum ConflictHandling:
  /** No special handling needed — types are compatible. */
  case Native

  /** Automatically converts between types. */
  case Converted

  /** Conversion possible but requires manual code. */
  case Manual

  /** Warning about potential issues. */
  case Warning

  /** Fundamentally incompatible types. */
  case Incompatible
