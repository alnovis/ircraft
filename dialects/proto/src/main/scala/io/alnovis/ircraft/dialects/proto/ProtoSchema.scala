package io.alnovis.ircraft.dialects.proto

/**
  * Represents a parsed Protocol Buffers `.proto` file.
  *
  * This is the input schema for the proto dialect. It captures the structural information
  * from a `.proto` file -- messages, enums, fields, and file-level options -- in a form
  * ready for lowering to the semantic IR via [[ProtoLowering]].
  *
  * @param name                 the file name (e.g., `"my_service.proto"`)
  * @param syntax               the protobuf syntax version (`proto2` or `proto3`)
  * @param packageName          the protobuf package name
  * @param javaPackage          the value of `option java_package`, if set
  * @param javaOuterClassname   the value of `option java_outer_classname`, if set
  * @param javaMultipleFiles    the value of `option java_multiple_files`
  * @param messages             the top-level message definitions
  * @param enums                the top-level enum definitions
  *
  * @see [[ProtoLowering]] for converting a [[ProtoFile]] to semantic IR
  * @see [[ProtoDialect]] for the algebra-based lowering
  */
case class ProtoFile(
  name: String,
  syntax: ProtoSyntax,
  packageName: String,
  javaPackage: Option[String],
  javaOuterClassname: Option[String],
  javaMultipleFiles: Boolean,
  messages: Vector[ProtoMessage],
  enums: Vector[ProtoEnum]
)

/**
  * The protobuf syntax version.
  *
  * @see [[ProtoSyntax.Proto2]]
  * @see [[ProtoSyntax.Proto3]]
  */
sealed abstract class ProtoSyntax

/**
  * Companion object containing [[ProtoSyntax]] variants.
  */
object ProtoSyntax {

  /** Protocol Buffers syntax version 2. */
  case object Proto2 extends ProtoSyntax

  /** Protocol Buffers syntax version 3. */
  case object Proto3 extends ProtoSyntax
}

/**
  * A Protocol Buffers message definition.
  *
  * Messages can contain fields, nested messages, nested enums, and oneof groups.
  * They are lowered to `TypeDeclF(Protocol)` by [[ProtoLowering]].
  *
  * @param name            the message name (e.g., `"UserRequest"`)
  * @param fields          the message fields
  * @param nestedMessages  nested message definitions
  * @param nestedEnums     nested enum definitions
  * @param oneofs          oneof groups within this message
  */
case class ProtoMessage(
  name: String,
  fields: Vector[ProtoField],
  nestedMessages: Vector[ProtoMessage],
  nestedEnums: Vector[ProtoEnum],
  oneofs: Vector[ProtoOneof]
)

/**
  * A single field within a Protocol Buffers message.
  *
  * @param name      the field name (e.g., `"user_id"`)
  * @param number    the field number (unique within the message)
  * @param fieldType the field's protobuf type
  * @param label     the field label (`required`, `optional`, or `repeated`)
  * @param typeName  the original type name from the `.proto` file for
  *                  message/enum references; `None` for scalar types
  */
case class ProtoField(
  name: String,
  number: Int,
  fieldType: ProtoType,
  label: ProtoLabel,
  typeName: Option[String]
)

/**
  * Field label in a Protocol Buffers message.
  *
  * @see [[ProtoLabel.Required]]
  * @see [[ProtoLabel.Optional]]
  * @see [[ProtoLabel.Repeated]]
  */
sealed abstract class ProtoLabel

/**
  * Companion object containing [[ProtoLabel]] variants.
  */
object ProtoLabel {

  /** The field is required (proto2 only; always present in the message). */
  case object Required extends ProtoLabel

  /** The field is optional (may or may not be present). */
  case object Optional extends ProtoLabel

  /** The field is repeated (zero or more values; maps are also represented as repeated). */
  case object Repeated extends ProtoLabel
}

/**
  * Protobuf field type, covering scalar types, message/enum references, and map types.
  *
  * Scalar types correspond to protobuf built-in types (`double`, `float`, `int32`, etc.).
  * Complex types reference other messages or enums by fully qualified name.
  *
  * @see [[ProtoType.Message]] for message type references
  * @see [[ProtoType.Enum]] for enum type references
  * @see [[ProtoType.Map]] for map field types
  */
sealed trait ProtoType

/**
  * Companion object containing all [[ProtoType]] variants.
  */
object ProtoType {

  /** Protobuf `double` type (64-bit floating point). */
  case object Double extends ProtoType

  /** Protobuf `float` type (32-bit floating point). */
  case object Float extends ProtoType

  /** Protobuf `int32` type (variable-length encoding, inefficient for negative numbers). */
  case object Int32 extends ProtoType

  /** Protobuf `int64` type (variable-length encoding, inefficient for negative numbers). */
  case object Int64 extends ProtoType

  /** Protobuf `uint32` type (variable-length unsigned 32-bit integer). */
  case object UInt32 extends ProtoType

  /** Protobuf `uint64` type (variable-length unsigned 64-bit integer). */
  case object UInt64 extends ProtoType

  /** Protobuf `sint32` type (variable-length encoding, efficient for negative numbers). */
  case object SInt32 extends ProtoType

  /** Protobuf `sint64` type (variable-length encoding, efficient for negative numbers). */
  case object SInt64 extends ProtoType

  /** Protobuf `fixed32` type (always 4 bytes; more efficient than `uint32` if values are often > 2^28). */
  case object Fixed32 extends ProtoType

  /** Protobuf `fixed64` type (always 8 bytes; more efficient than `uint64` if values are often > 2^56). */
  case object Fixed64 extends ProtoType

  /** Protobuf `sfixed32` type (always 4 bytes, signed). */
  case object SFixed32 extends ProtoType

  /** Protobuf `sfixed64` type (always 8 bytes, signed). */
  case object SFixed64 extends ProtoType

  /** Protobuf `bool` type. */
  case object Bool extends ProtoType

  /** Protobuf `string` type (UTF-8 encoded). */
  case object String extends ProtoType

  /** Protobuf `bytes` type (arbitrary byte sequence). */
  case object Bytes extends ProtoType

  /**
    * A reference to another protobuf message type.
    *
    * @param fqn the fully qualified name of the referenced message
    */
  case class Message(fqn: scala.Predef.String) extends ProtoType

  /**
    * A reference to a protobuf enum type.
    *
    * @param fqn the fully qualified name of the referenced enum
    */
  case class Enum(fqn: scala.Predef.String) extends ProtoType

  /**
    * A protobuf map field type.
    *
    * @param key   the key type (must be a scalar type)
    * @param value the value type (can be any protobuf type)
    */
  case class Map(key: ProtoType, value: ProtoType) extends ProtoType
}

/**
  * A Protocol Buffers enum definition.
  *
  * @param name   the enum name (e.g., `"Status"`)
  * @param values the enum constants with their numeric values
  */
case class ProtoEnum(name: String, values: Vector[ProtoEnumValue])

/**
  * A single constant in a Protocol Buffers enum.
  *
  * @param name   the constant name (e.g., `"STATUS_UNKNOWN"`)
  * @param number the numeric value assigned to this constant
  */
case class ProtoEnumValue(name: String, number: Int)

/**
  * A Protocol Buffers `oneof` group within a message.
  *
  * A oneof represents a set of mutually exclusive fields -- at most one field
  * in the group can be set at a time. Lowered to a sum type by [[ProtoDialect]].
  *
  * @param name   the oneof group name (e.g., `"result"`)
  * @param fields the fields within the oneof group
  */
case class ProtoOneof(name: String, fields: Vector[ProtoField])
