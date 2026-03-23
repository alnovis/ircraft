package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf enum declaration. Maps to MergedEnum. */
case class EnumOp(
  name: String,
  presentInVersions: Set[String],
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
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofList(values.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + values.size

object EnumOp:

  @targetName("create")
  def apply(
    name: String,
    presentInVersions: Set[String],
    values: Vector[EnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): EnumOp = new EnumOp(
    name,
    presentInVersions,
    regions = Vector(Region("values", values)),
    attributes,
    span
  )

/** Protobuf enum value. Maps to MergedEnumValue. */
case class EnumValueOp(
  name: String,
  number: Int,
  presentInVersions: Set[String] = Set.empty,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = ProtoDialect.Kinds.EnumValue
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      number,
      ContentHash.ofSet(presentInVersions)
    )

  val estimatedSize: Int = 1
