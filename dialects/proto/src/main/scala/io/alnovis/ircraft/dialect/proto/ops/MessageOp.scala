package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf message declaration. Maps to MergedMessage. */
case class MessageOp(
  name: String,
  presentInVersions: Set[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Message

  lazy val fields: Vector[FieldOp]           = regionOps("fields")
  lazy val oneofs: Vector[OneofOp]           = regionOps("oneofs")
  lazy val nestedMessages: Vector[MessageOp] = regionOps("nestedMessages")
  lazy val nestedEnums: Vector[EnumOp]       = regionOps("nestedEnums")

  override def mapChildren(f: Operation => Operation): MessageOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable),
      ContentHash.ofList(oneofs.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedMessages.toList)(using Operation.operationHashable),
      ContentHash.ofList(nestedEnums.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + fields.map(_.estimatedSize).sum + nestedMessages.map(_.estimatedSize).sum

  def hasOneofs: Boolean      = oneofs.nonEmpty
  def hasNestedTypes: Boolean = nestedMessages.nonEmpty || nestedEnums.nonEmpty

object MessageOp:

  @targetName("create")
  def apply(
    name: String,
    presentInVersions: Set[String],
    fields: Vector[FieldOp] = Vector.empty,
    oneofs: Vector[OneofOp] = Vector.empty,
    nestedMessages: Vector[MessageOp] = Vector.empty,
    nestedEnums: Vector[EnumOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): MessageOp = new MessageOp(
    name,
    presentInVersions,
    regions = Vector(
      Region("fields", fields),
      Region("oneofs", oneofs),
      Region("nestedMessages", nestedMessages),
      Region("nestedEnums", nestedEnums)
    ),
    attributes,
    span
  )
