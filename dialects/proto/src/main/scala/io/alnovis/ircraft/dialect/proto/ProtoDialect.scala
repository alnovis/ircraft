package io.alnovis.ircraft.dialect.proto

import io.alnovis.ircraft.core.*

/** Protobuf schema dialect -- represents a single .proto file. */
object ProtoDialect extends Dialect:

  val namespace: String   = "proto"
  val description: String = "Protobuf schema representation"

  object Kinds:
    val File: NodeKind      = NodeKind(namespace, "file")
    val Message: NodeKind   = NodeKind(namespace, "message")
    val Field: NodeKind     = NodeKind(namespace, "field")
    val Enum: NodeKind      = NodeKind(namespace, "enum")
    val EnumValue: NodeKind = NodeKind(namespace, "enum_value")
    val Oneof: NodeKind     = NodeKind(namespace, "oneof")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.File,
    Kinds.Message,
    Kinds.Field,
    Kinds.Enum,
    Kinds.EnumValue,
    Kinds.Oneof
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to proto dialect"))
    else Nil
