package io.alnovis.ircraft.dialect.semantic.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect

/** A source file containing type declarations. */
case class FileOp(
    packageName: String,
    types: Vector[Operation] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind          = SemanticDialect.Kinds.File
  val regions: Vector[Region] = Vector(Region("types", types))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(packageName),
      ContentHash.ofList(types.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = types.map(_.width).sum
