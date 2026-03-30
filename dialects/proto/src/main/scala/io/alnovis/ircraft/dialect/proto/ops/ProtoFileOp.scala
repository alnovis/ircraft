package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect

/** Top-level protobuf file representation. */
case class ProtoFileOp(
  protoPackage: String,
  syntax: ProtoSyntax,
  options: Map[String, String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.File

  lazy val messages: Vector[MessageOp] = regionOps("messages")
  lazy val enums: Vector[EnumOp]       = regionOps("enums")

  override def mapChildren(f: Operation => Operation): ProtoFileOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(protoPackage),
      summon[ContentHashable[ProtoSyntax]].contentHash(syntax),
      ContentHash.ofList(options.toList.sorted.map((k, v) => ContentHash.combine(ContentHash.ofString(k), ContentHash.ofString(v)))),
      ContentHash.ofList(messages.toList)(using Operation.operationHashable),
      ContentHash.ofList(enums.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int =
    1 + messages.map(_.estimatedSize).sum + enums.map(_.estimatedSize).sum

object ProtoFileOp:

  @targetName("create")
  def apply(
    protoPackage: String,
    syntax: ProtoSyntax = ProtoSyntax.Proto3,
    options: Map[String, String] = Map.empty,
    messages: Vector[MessageOp] = Vector.empty,
    enums: Vector[EnumOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): ProtoFileOp = new ProtoFileOp(
    protoPackage,
    syntax,
    options,
    regions = Vector(
      Region("messages", messages),
      Region("enums", enums)
    ),
    attributes,
    span
  )
