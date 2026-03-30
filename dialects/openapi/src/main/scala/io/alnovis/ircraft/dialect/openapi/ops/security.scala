package io.alnovis.ircraft.dialect.openapi.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.OpenApiDialect

/** OpenAPI security scheme (leaf). */
case class SecuritySchemeOp(
  name: String,
  schemeType: SecuritySchemeType,
  description: Option[String] = None,
  details: Map[String, String] = Map.empty,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.SecurityScheme
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[SecuritySchemeType]].contentHash(schemeType),
      ContentHash.ofOption(description),
      ContentHash.ofList(details.toList.sorted.map((k, v) =>
        ContentHash.combine(ContentHash.ofString(k), ContentHash.ofString(v))
      ))
    )

  val estimatedSize: Int = 1

/** OpenAPI security requirement (leaf). */
case class SecurityRequirementOp(
  schemeName: String,
  scopes: List[String] = Nil,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.SecurityRequirement
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(schemeName),
      ContentHash.ofList(scopes)
    )

  val estimatedSize: Int = 1
