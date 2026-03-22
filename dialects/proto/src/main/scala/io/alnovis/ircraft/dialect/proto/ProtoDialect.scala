package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*

/** Proto Dialect: high-level protobuf schema representation.
  *
  * Operations:
  *   - SchemaOp: top-level schema container
  *   - MessageOp: message declaration
  *   - FieldOp: field declaration
  *   - EnumOp: enum declaration
  *   - EnumValueOp: enum value
  *   - OneofOp: oneof group
  *   - ConflictEnumOp: generated conflict enum
  */
object ProtoDialect extends Dialect:

  val namespace: String   = "proto"
  val description: String = "High-level protobuf schema representation"

  object Kinds:
    val Schema: NodeKind       = NodeKind(namespace, "schema")
    val Message: NodeKind      = NodeKind(namespace, "message")
    val Field: NodeKind        = NodeKind(namespace, "field")
    val Enum: NodeKind         = NodeKind(namespace, "enum")
    val EnumValue: NodeKind    = NodeKind(namespace, "enum_value")
    val Oneof: NodeKind        = NodeKind(namespace, "oneof")
    val ConflictEnum: NodeKind = NodeKind(namespace, "conflict_enum")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.Schema,
    Kinds.Message,
    Kinds.Field,
    Kinds.Enum,
    Kinds.EnumValue,
    Kinds.Oneof,
    Kinds.ConflictEnum,
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to proto dialect"))
    else Nil
