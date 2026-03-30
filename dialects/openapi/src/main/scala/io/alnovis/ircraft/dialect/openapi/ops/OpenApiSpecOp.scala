package io.alnovis.ircraft.dialect.openapi.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.OpenApiDialect

/** Top-level OpenAPI specification. */
case class OpenApiSpecOp(
  title: String,
  version: String,
  description: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.Spec

  lazy val schemas: Vector[Operation]              = regionOps("schemas")
  lazy val paths: Vector[PathOp]                   = regionOps("paths")
  lazy val securitySchemes: Vector[SecuritySchemeOp] = regionOps("securitySchemes")
  lazy val servers: Vector[ServerOp]               = regionOps("servers")
  lazy val tags: Vector[TagOp]                     = regionOps("tags")
  lazy val examples: Vector[ExampleOp]             = regionOps("examples")

  override def mapChildren(f: Operation => Operation): OpenApiSpecOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(title),
      ContentHash.ofString(version),
      ContentHash.ofOption(description),
      ContentHash.ofList(schemas.toList)(using Operation.operationHashable),
      ContentHash.ofList(paths.toList)(using Operation.operationHashable),
      ContentHash.ofList(securitySchemes.toList)(using Operation.operationHashable),
      ContentHash.ofList(servers.toList)(using Operation.operationHashable),
      ContentHash.ofList(tags.toList)(using Operation.operationHashable),
      ContentHash.ofList(examples.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int =
    1 + schemas.map(_.estimatedSize).sum +
      paths.map(_.estimatedSize).sum +
      securitySchemes.map(_.estimatedSize).sum +
      servers.map(_.estimatedSize).sum +
      tags.map(_.estimatedSize).sum +
      examples.map(_.estimatedSize).sum

object OpenApiSpecOp:

  @targetName("create")
  def apply(
    title: String,
    version: String,
    description: Option[String] = None,
    schemas: Vector[Operation] = Vector.empty,
    paths: Vector[PathOp] = Vector.empty,
    securitySchemes: Vector[SecuritySchemeOp] = Vector.empty,
    servers: Vector[ServerOp] = Vector.empty,
    tags: Vector[TagOp] = Vector.empty,
    examples: Vector[ExampleOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): OpenApiSpecOp = new OpenApiSpecOp(
    title,
    version,
    description,
    regions = Vector(
      Region("schemas", schemas),
      Region("paths", paths),
      Region("securitySchemes", securitySchemes),
      Region("servers", servers),
      Region("tags", tags),
      Region("examples", examples)
    ),
    attributes,
    span
  )
