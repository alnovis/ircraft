package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect
import io.alnovis.ircraft.dialect.proto.types.ProtoSyntax

/** Top-level schema container. Maps to MergedSchema.
  *
  * @param versions
  *   ordered list of version identifiers (e.g., "v1", "v2")
  * @param versionSyntax
  *   proto syntax per version
  * @param messages
  *   top-level messages
  * @param enums
  *   top-level enums
  * @param conflictEnums
  *   generated conflict enums for INT_ENUM conflicts
  */
case class SchemaOp(
    versions: List[String],
    versionSyntax: Map[String, ProtoSyntax] = Map.empty,
    messages: Vector[MessageOp] = Vector.empty,
    enums: Vector[EnumOp] = Vector.empty,
    conflictEnums: Vector[ConflictEnumOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Schema

  val regions: Vector[Region] = Vector(
    Region("messages", messages),
    Region("enums", enums),
    Region("conflictEnums", conflictEnums),
  )

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofList(versions),
      ContentHash.ofList(messages.toList)(using Operation.operationHashable),
      ContentHash.ofList(enums.toList)(using Operation.operationHashable),
      ContentHash.ofList(conflictEnums.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = messages.map(_.width).sum + enums.map(_.width).sum
