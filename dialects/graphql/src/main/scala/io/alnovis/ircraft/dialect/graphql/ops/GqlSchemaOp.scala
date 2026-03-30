package io.alnovis.ircraft.dialect.graphql.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.GraphQlDialect

/** Top-level GraphQL schema representation. */
case class GqlSchemaOp(
  queryType: String,
  mutationType: Option[String],
  subscriptionType: Option[String],
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = GraphQlDialect.Kinds.Schema

  lazy val types: Vector[Operation]              = regionOps("types")
  lazy val directives: Vector[DirectiveDefOp]    = regionOps("directives")

  override def mapChildren(f: Operation => Operation): GqlSchemaOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(queryType),
      ContentHash.ofOption(mutationType),
      ContentHash.ofOption(subscriptionType),
      ContentHash.ofList(types.toList)(using Operation.operationHashable),
      ContentHash.ofList(directives.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int =
    1 + types.map(_.estimatedSize).sum + directives.map(_.estimatedSize).sum

object GqlSchemaOp:

  @targetName("create")
  def apply(
    queryType: String,
    mutationType: Option[String] = None,
    subscriptionType: Option[String] = None,
    types: Vector[Operation] = Vector.empty,
    directives: Vector[DirectiveDefOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): GqlSchemaOp = new GqlSchemaOp(
    queryType,
    mutationType,
    subscriptionType,
    regions = Vector(
      Region("types", types),
      Region("directives", directives)
    ),
    attributes,
    span
  )
