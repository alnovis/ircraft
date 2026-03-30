package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf enum value (leaf). */
case class EnumValueOp(
  name: String,
  number: Int,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = ProtoDialect.Kinds.EnumValue
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofInt(number)
    )

  val estimatedSize: Int = 1

/** Protobuf enum declaration. */
case class EnumOp(
  name: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Enum

  lazy val values: Vector[EnumValueOp] = regionOps("values")

  override def mapChildren(f: Operation => Operation): EnumOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofList(values.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + values.map(_.estimatedSize).sum

object EnumOp:

  @targetName("create")
  def apply(
    name: String,
    values: Vector[EnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): EnumOp = new EnumOp(
    name,
    regions = Vector(Region("values", values)),
    attributes,
    span
  )
