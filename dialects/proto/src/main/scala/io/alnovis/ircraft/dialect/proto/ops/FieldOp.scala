package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf field declaration (leaf). */
case class FieldOp(
  name: String,
  number: Int,
  fieldType: TypeRef,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = ProtoDialect.Kinds.Field
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofInt(number),
      summon[ContentHashable[TypeRef]].contentHash(fieldType)
    )

  val estimatedSize: Int = 1
