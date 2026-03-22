package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf message declaration. Maps to MergedMessage. */
case class MessageOp(
    name: String,
    presentInVersions: Set[String],
    fields: Vector[FieldOp] = Vector.empty,
    oneofs: Vector[OneofOp] = Vector.empty,
    nestedMessages: Vector[MessageOp] = Vector.empty,
    nestedEnums: Vector[EnumOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Message

  val regions: Vector[Region] = Vector(
    Region("fields", fields),
    Region("oneofs", oneofs),
    Region("nestedMessages", nestedMessages),
    Region("nestedEnums", nestedEnums),
  )

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable),
      ContentHash.ofList(oneofs.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedMessages.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedEnums.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = 1 + fields.map(_.width).sum + nestedMessages.map(_.width).sum

  def hasOneofs: Boolean      = oneofs.nonEmpty
  def hasNestedTypes: Boolean = nestedMessages.nonEmpty || nestedEnums.nonEmpty
