package io.alnovis.ircraft.dialects.proto

/** Proto source dialect -- pure ADT, no ircraft dependencies in the model. */

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

enum ProtoSyntax:
  case Proto2, Proto3

case class ProtoMessage(
  name: String,
  fields: Vector[ProtoField],
  nestedMessages: Vector[ProtoMessage],
  nestedEnums: Vector[ProtoEnum],
  oneofs: Vector[ProtoOneof]
)

case class ProtoField(
  name: String,
  number: Int,
  fieldType: ProtoType,
  label: ProtoLabel,
  typeName: Option[String] // FQN for message/enum references
)

enum ProtoLabel:
  case Required, Optional, Repeated

sealed trait ProtoType

object ProtoType:
  // scalars
  case object Double   extends ProtoType
  case object Float    extends ProtoType
  case object Int32    extends ProtoType
  case object Int64    extends ProtoType
  case object UInt32   extends ProtoType
  case object UInt64   extends ProtoType
  case object SInt32   extends ProtoType
  case object SInt64   extends ProtoType
  case object Fixed32  extends ProtoType
  case object Fixed64  extends ProtoType
  case object SFixed32 extends ProtoType
  case object SFixed64 extends ProtoType
  case object Bool     extends ProtoType
  case object String   extends ProtoType
  case object Bytes    extends ProtoType

  // composite
  case class Message(fqn: scala.Predef.String)     extends ProtoType
  case class Enum(fqn: scala.Predef.String)        extends ProtoType
  case class Map(key: ProtoType, value: ProtoType) extends ProtoType

case class ProtoEnum(
  name: String,
  values: Vector[ProtoEnumValue]
)

case class ProtoEnumValue(
  name: String,
  number: Int
)

case class ProtoOneof(
  name: String,
  fields: Vector[ProtoField]
)
