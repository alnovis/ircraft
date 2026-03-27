package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf oneof group. Maps to MergedOneof. */
case class OneofOp(
  protoName: String,
  javaName: String,
  caseEnumName: String,
  presentInVersions: Set[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Oneof

  lazy val fields: Vector[FieldOp] = regionOps("fields")

  override def mapChildren(f: Operation => Operation): OneofOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(protoName),
      ContentHash.ofString(javaName),
      ContentHash.ofString(caseEnumName),
      ContentHash.ofSet(presentInVersions),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + fields.size

object OneofOp:

  @targetName("create")
  def apply(
    protoName: String,
    javaName: String,
    caseEnumName: String,
    presentInVersions: Set[String],
    fields: Vector[FieldOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): OneofOp = new OneofOp(
    protoName,
    javaName,
    caseEnumName,
    presentInVersions,
    regions = Vector(Region("fields", fields)),
    attributes,
    span
  )
