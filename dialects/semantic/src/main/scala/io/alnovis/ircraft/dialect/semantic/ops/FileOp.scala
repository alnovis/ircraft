package io.alnovis.ircraft.dialect.semantic.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect

/** A source file containing type declarations. */
case class FileOp(
  packageName: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = SemanticDialect.Kinds.File

  lazy val types: Vector[Operation] = regionOps("types")

  override def mapChildren(f: Operation => Operation): FileOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(packageName),
      ContentHash.ofList(types.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = types.map(_.estimatedSize).sum

object FileOp:

  @targetName("create")
  def apply(
    packageName: String,
    types: Vector[Operation] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): FileOp = new FileOp(
    packageName,
    regions = Vector(Region("types", types)),
    attributes,
    span
  )
