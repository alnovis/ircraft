package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Generated conflict enum for INT_ENUM type conflicts. Maps to ConflictEnumInfo. */
case class ConflictEnumOp(
  fieldName: String,
  enumName: String,
  messageName: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.ConflictEnum

  lazy val values: Vector[EnumValueOp] = regionOps("values")

  override def mapChildren(f: Operation => Operation): ConflictEnumOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(fieldName),
      ContentHash.ofString(enumName),
      ContentHash.ofString(messageName),
      ContentHash.ofList(values.toList)(using Operation.operationHashable)
    )

  lazy val width: Int = 1 + values.size

object ConflictEnumOp:

  @targetName("create")
  def apply(
    fieldName: String,
    enumName: String,
    messageName: String,
    values: Vector[EnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): ConflictEnumOp = new ConflictEnumOp(
    fieldName,
    enumName,
    messageName,
    regions = Vector(Region("values", values)),
    attributes,
    span
  )
