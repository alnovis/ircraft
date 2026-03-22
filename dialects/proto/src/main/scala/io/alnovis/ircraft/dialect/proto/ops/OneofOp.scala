package io.alnovis.ircraft.dialect.proto.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf oneof group. Maps to MergedOneof. */
case class OneofOp(
    protoName: String,
    javaName: String,
    caseEnumName: String,
    presentInVersions: Set[String],
    fields: Vector[FieldOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Oneof

  val regions: Vector[Region] = Vector(Region("fields", fields))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(protoName),
      ContentHash.ofString(javaName),
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = 1 + fields.size
