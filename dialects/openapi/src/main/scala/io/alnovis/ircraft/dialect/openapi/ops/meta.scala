package io.alnovis.ircraft.dialect.openapi.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.OpenApiDialect

/** OpenAPI server (leaf). */
case class ServerOp(
  url: String,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Server
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(url),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** OpenAPI tag (leaf). */
case class TagOp(
  name: String,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Tag
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** OpenAPI example (leaf). */
case class ExampleOp(
  name: String,
  value: String,
  summary: Option[String] = None,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Example
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofString(value),
      ContentHash.ofOption(summary),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1
