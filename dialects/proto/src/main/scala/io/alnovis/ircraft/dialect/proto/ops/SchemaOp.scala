package io.alnovis.ircraft.dialect.proto.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ProtoDialect
import io.alnovis.ircraft.dialect.proto.types.ProtoSyntax

/**
  * Top-level schema container. Maps to MergedSchema.
  *
  * @param versions
  *   ordered list of version identifiers (e.g., "v1", "v2")
  * @param versionSyntax
  *   proto syntax per version
  */
case class SchemaOp(
  versions: List[String],
  versionSyntax: Map[String, ProtoSyntax],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = ProtoDialect.Kinds.Schema

  lazy val messages: Vector[MessageOp]           = regionOps("messages")
  lazy val enums: Vector[EnumOp]                 = regionOps("enums")
  lazy val conflictEnums: Vector[ConflictEnumOp] = regionOps("conflictEnums")

  override def mapChildren(f: Operation => Operation): SchemaOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofList(versions),
      versionSyntax.foldLeft(0)((acc, kv) => acc ^ ContentHash.combine(ContentHash.ofString(kv._1), kv._2.ordinal)),
      ContentHash.ofList(messages.toList)(using Operation.operationHashable),
      ContentHash.ofList(enums.toList)(using Operation.operationHashable),
      ContentHash.ofList(conflictEnums.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = messages.map(_.estimatedSize).sum + enums.map(_.estimatedSize).sum

object SchemaOp:

  @targetName("create")
  def apply(
    versions: List[String],
    versionSyntax: Map[String, ProtoSyntax] = Map.empty,
    messages: Vector[MessageOp] = Vector.empty,
    enums: Vector[EnumOp] = Vector.empty,
    conflictEnums: Vector[ConflictEnumOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): SchemaOp = new SchemaOp(
    versions,
    versionSyntax,
    regions = Vector(
      Region("messages", messages),
      Region("enums", enums),
      Region("conflictEnums", conflictEnums)
    ),
    attributes,
    span
  )
