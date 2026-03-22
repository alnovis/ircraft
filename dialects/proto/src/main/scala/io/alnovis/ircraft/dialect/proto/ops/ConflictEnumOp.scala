package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Generated conflict enum for INT_ENUM type conflicts. Maps to ConflictEnumInfo. */
case class ConflictEnumOp(
    fieldName: String,
    enumName: String,
    messageName: String,
    values: Vector[EnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.ConflictEnum

  val regions: Vector[Region] = Vector(Region("values", values))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(fieldName),
      ContentHash.ofString(enumName),
      ContentHash.ofString(messageName),
      ContentHash.ofList(values.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = 1 + values.size
