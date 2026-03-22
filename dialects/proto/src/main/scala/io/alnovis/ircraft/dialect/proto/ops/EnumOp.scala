package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf enum declaration. Maps to MergedEnum. */
case class EnumOp(
    name: String,
    presentInVersions: Set[String],
    values: Vector[EnumValueOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Enum

  val regions: Vector[Region] = Vector(Region("values", values))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofList(values.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = 1 + values.size

/** Protobuf enum value. Maps to MergedEnumValue. */
case class EnumValueOp(
    name: String,
    number: Int,
    presentInVersions: Set[String] = Set.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.EnumValue

  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      number,
      ContentHash.ofSet(presentInVersions),
    )

  val width: Int = 1
