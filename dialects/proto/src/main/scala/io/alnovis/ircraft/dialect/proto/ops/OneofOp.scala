package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Protobuf oneof group. */
case class OneofOp(
  name: String,
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
      ContentHash.ofString(name),
      ContentHash.ofList(fields.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + fields.map(_.estimatedSize).sum

object OneofOp:

  @targetName("create")
  def apply(
    name: String,
    fields: Vector[FieldOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): OneofOp = new OneofOp(
    name,
    regions = Vector(Region("fields", fields)),
    attributes,
    span
  )
