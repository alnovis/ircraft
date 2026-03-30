package io.alnovis.ircraft.dialect.openapi.ops

import scala.annotation.targetName

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.OpenApiDialect

/** OpenAPI path item -- groups operations under a URL path pattern. */
case class PathOp(
  pathPattern: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.Path

  lazy val operations: Vector[OperationOp] = regionOps("operations")

  override def mapChildren(f: Operation => Operation): PathOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(pathPattern),
      ContentHash.ofList(operations.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + operations.map(_.estimatedSize).sum

object PathOp:

  @targetName("create")
  def apply(
    pathPattern: String,
    operations: Vector[OperationOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): PathOp = new PathOp(
    pathPattern,
    regions = Vector(Region("operations", operations)),
    attributes,
    span
  )

/** OpenAPI operation -- a single HTTP method on a path. */
case class OperationOp(
  httpMethod: HttpMethod,
  operationId: Option[String],
  summary: Option[String],
  description: Option[String],
  tags: List[String],
  deprecated: Boolean,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.Operation

  lazy val parameters: Vector[ParameterOp]     = regionOps("parameters")
  lazy val responses: Vector[ResponseOp]       = regionOps("responses")
  lazy val requestBodies: Vector[RequestBodyOp] = regionOps("requestBodies")
  lazy val mediaTypes: Vector[MediaTypeOp]     = regionOps("mediaTypes")

  override def mapChildren(f: Operation => Operation): OperationOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      summon[ContentHashable[HttpMethod]].contentHash(httpMethod),
      ContentHash.ofOption(operationId),
      ContentHash.ofOption(summary),
      ContentHash.ofOption(description),
      ContentHash.ofList(tags),
      ContentHash.ofBoolean(deprecated),
      ContentHash.ofList(parameters.toList)(using Operation.operationHashable),
      ContentHash.ofList(responses.toList)(using Operation.operationHashable),
      ContentHash.ofList(requestBodies.toList)(using Operation.operationHashable),
      ContentHash.ofList(mediaTypes.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int =
    1 + parameters.map(_.estimatedSize).sum +
      responses.map(_.estimatedSize).sum +
      requestBodies.map(_.estimatedSize).sum +
      mediaTypes.map(_.estimatedSize).sum

object OperationOp:

  @targetName("create")
  def apply(
    httpMethod: HttpMethod,
    operationId: Option[String] = None,
    summary: Option[String] = None,
    description: Option[String] = None,
    tags: List[String] = Nil,
    deprecated: Boolean = false,
    parameters: Vector[ParameterOp] = Vector.empty,
    responses: Vector[ResponseOp] = Vector.empty,
    requestBodies: Vector[RequestBodyOp] = Vector.empty,
    mediaTypes: Vector[MediaTypeOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): OperationOp = new OperationOp(
    httpMethod,
    operationId,
    summary,
    description,
    tags,
    deprecated,
    regions = Vector(
      Region("parameters", parameters),
      Region("responses", responses),
      Region("requestBodies", requestBodies),
      Region("mediaTypes", mediaTypes)
    ),
    attributes,
    span
  )

/** OpenAPI parameter (leaf). */
case class ParameterOp(
  name: String,
  location: ParameterLocation,
  paramType: TypeRef,
  required: Boolean,
  description: Option[String] = None,
  deprecated: Boolean = false,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Parameter
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[ParameterLocation]].contentHash(location),
      summon[ContentHashable[TypeRef]].contentHash(paramType),
      ContentHash.ofBoolean(required),
      ContentHash.ofOption(description),
      ContentHash.ofBoolean(deprecated)
    )

  val estimatedSize: Int = 1

/** OpenAPI request body. */
case class RequestBodyOp(
  description: Option[String],
  required: Boolean,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.RequestBody

  lazy val mediaTypes: Vector[MediaTypeOp] = regionOps("mediaTypes")

  override def mapChildren(f: Operation => Operation): RequestBodyOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofOption(description),
      ContentHash.ofBoolean(required),
      ContentHash.ofList(mediaTypes.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = 1 + mediaTypes.map(_.estimatedSize).sum

object RequestBodyOp:

  @targetName("create")
  def apply(
    description: Option[String] = None,
    required: Boolean = false,
    mediaTypes: Vector[MediaTypeOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): RequestBodyOp = new RequestBodyOp(
    description,
    required,
    regions = Vector(Region("mediaTypes", mediaTypes)),
    attributes,
    span
  )

/** OpenAPI media type (leaf). */
case class MediaTypeOp(
  mediaType: String,
  schemaRef: TypeRef,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.MediaType
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(mediaType),
      summon[ContentHashable[TypeRef]].contentHash(schemaRef)
    )

  val estimatedSize: Int = 1

/** OpenAPI response. */
case class ResponseOp(
  statusCode: String,
  description: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:

  val kind: NodeKind = OpenApiDialect.Kinds.Response

  lazy val mediaTypes: Vector[MediaTypeOp] = regionOps("mediaTypes")
  lazy val headers: Vector[HeaderOp]       = regionOps("headers")

  override def mapChildren(f: Operation => Operation): ResponseOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(statusCode),
      ContentHash.ofString(description),
      ContentHash.ofList(mediaTypes.toList)(using Operation.operationHashable),
      ContentHash.ofList(headers.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int =
    1 + mediaTypes.map(_.estimatedSize).sum + headers.map(_.estimatedSize).sum

object ResponseOp:

  @targetName("create")
  def apply(
    statusCode: String,
    description: String,
    mediaTypes: Vector[MediaTypeOp] = Vector.empty,
    headers: Vector[HeaderOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): ResponseOp = new ResponseOp(
    statusCode,
    description,
    regions = Vector(
      Region("mediaTypes", mediaTypes),
      Region("headers", headers)
    ),
    attributes,
    span
  )

/** OpenAPI header (leaf). */
case class HeaderOp(
  name: String,
  headerType: TypeRef,
  required: Boolean,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Header
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      summon[ContentHashable[TypeRef]].contentHash(headerType),
      ContentHash.ofBoolean(required),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1

/** OpenAPI link (leaf). */
case class LinkOp(
  name: String,
  operationId: String,
  parameters: Map[String, String] = Map.empty,
  description: Option[String] = None,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind          = OpenApiDialect.Kinds.Link
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofString(operationId),
      ContentHash.ofList(parameters.toList.sorted.map((k, v) =>
        ContentHash.combine(ContentHash.ofString(k), ContentHash.ofString(v))
      )),
      ContentHash.ofOption(description)
    )

  val estimatedSize: Int = 1
