package io.alnovis.ircraft.dialect.openapi.dsl

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.ops.*

import scala.collection.mutable

/** Builder DSL for constructing OpenAPI IR. */
object OpenApiSchema:

  def spec(title: String, version: String, description: Option[String] = None)(
    f: SpecBuilder => Unit
  ): OpenApiSpecOp =
    val builder = SpecBuilder(title, version, description)
    f(builder)
    builder.build()

class SpecBuilder(title: String, version: String, description: Option[String]):
  private val servers = mutable.ArrayBuffer[ServerOp]()
  private val tags = mutable.ArrayBuffer[TagOp]()
  private val securitySchemes = mutable.ArrayBuffer[SecuritySchemeOp]()
  private val schemas = mutable.ArrayBuffer[Operation]()
  private val paths = mutable.ArrayBuffer[PathOp]()

  def server(url: String, serverDescription: Option[String] = None): Unit =
    servers += ServerOp(url, serverDescription)

  def tag(name: String, tagDescription: Option[String] = None): Unit =
    tags += TagOp(name, tagDescription)

  def securityScheme(
    name: String,
    schemeType: SecuritySchemeType,
    details: Map[String, String] = Map.empty
  ): Unit =
    securitySchemes += SecuritySchemeOp(name, schemeType, details = details)

  def schema(name: String)(f: SchemaObjectBuilder => Unit): Unit =
    val builder = SchemaObjectBuilder(name)
    f(builder)
    schemas += builder.build()

  def enum_(name: String, baseType: TypeRef = TypeRef.STRING)(f: EnumBuilder => Unit): Unit =
    val builder = EnumBuilder(name, baseType)
    f(builder)
    schemas += builder.build()

  def composition(compositionKind: CompositionKind)(f: CompositionBuilder => Unit): Unit =
    val builder = CompositionBuilder(compositionKind)
    f(builder)
    schemas += builder.build()

  def path(pathPattern: String)(f: PathBuilder => Unit): Unit =
    val builder = PathBuilder(pathPattern)
    f(builder)
    paths += builder.build()

  def build(): OpenApiSpecOp = OpenApiSpecOp(
    title,
    version,
    description,
    schemas = schemas.toVector,
    paths = paths.toVector,
    securitySchemes = securitySchemes.toVector,
    servers = servers.toVector,
    tags = tags.toVector
  )

class SchemaObjectBuilder(name: String):
  private val properties = mutable.ArrayBuffer[SchemaPropertyOp]()

  def property(
    name: String,
    fieldType: TypeRef,
    required: Boolean = false,
    propertyDescription: Option[String] = None,
    defaultValue: Option[String] = None
  ): Unit =
    properties += SchemaPropertyOp(name, fieldType, required, propertyDescription, defaultValue)

  def build(): SchemaObjectOp = SchemaObjectOp(name, properties.toVector)

class EnumBuilder(name: String, baseType: TypeRef):
  private val values = mutable.ArrayBuffer[SchemaEnumValueOp]()

  def value(name: String, rawValue: String = ""): Unit =
    val effectiveValue = if rawValue.isEmpty then name else rawValue
    values += SchemaEnumValueOp(name, effectiveValue)

  def build(): SchemaEnumOp = SchemaEnumOp(name, baseType, values.toVector)

class CompositionBuilder(compositionKind: CompositionKind):
  private val compositionSchemas = mutable.ArrayBuffer[Operation]()
  private var discProperty: Option[String] = None
  private var discMapping: Map[String, String] = Map.empty

  def schema(name: String)(f: SchemaObjectBuilder => Unit): Unit =
    val builder = SchemaObjectBuilder(name)
    f(builder)
    compositionSchemas += builder.build()

  def schemaRef(name: String): Unit =
    compositionSchemas += SchemaObjectOp(name)

  def discriminator(propertyName: String, mapping: Map[String, String] = Map.empty): Unit =
    discProperty = Some(propertyName)
    discMapping = mapping

  def build(): SchemaCompositionOp = SchemaCompositionOp(
    compositionKind,
    discriminatorProperty = discProperty,
    discriminatorMapping = discMapping,
    schemas = compositionSchemas.toVector
  )

class PathBuilder(pathPattern: String):
  private val operations = mutable.ArrayBuffer[OperationOp]()

  def get(operationId: String)(f: OperationBuilder => Unit): Unit =
    addOperation(HttpMethod.Get, operationId, f)

  def post(operationId: String)(f: OperationBuilder => Unit): Unit =
    addOperation(HttpMethod.Post, operationId, f)

  def put(operationId: String)(f: OperationBuilder => Unit): Unit =
    addOperation(HttpMethod.Put, operationId, f)

  def delete(operationId: String)(f: OperationBuilder => Unit): Unit =
    addOperation(HttpMethod.Delete, operationId, f)

  def patch(operationId: String)(f: OperationBuilder => Unit): Unit =
    addOperation(HttpMethod.Patch, operationId, f)

  private def addOperation(method: HttpMethod, operationId: String, f: OperationBuilder => Unit): Unit =
    val builder = OperationBuilder(method, operationId)
    f(builder)
    operations += builder.build()

  def build(): PathOp = PathOp(pathPattern, operations.toVector)

class OperationBuilder(httpMethod: HttpMethod, operationId: String):
  private var summaryText: Option[String] = None
  private var descriptionText: Option[String] = None
  private val operationTags = mutable.ArrayBuffer[String]()
  private var isDeprecated: Boolean = false
  private val parameters = mutable.ArrayBuffer[ParameterOp]()
  private val requestBodies = mutable.ArrayBuffer[RequestBodyOp]()
  private val responses = mutable.ArrayBuffer[ResponseOp]()

  def summary(s: String): Unit =
    summaryText = Some(s)

  def description(d: String): Unit =
    descriptionText = Some(d)

  def tag(t: String): Unit =
    operationTags += t

  def deprecated(): Unit =
    isDeprecated = true

  def queryParam(
    name: String,
    paramType: TypeRef,
    required: Boolean = false,
    paramDescription: Option[String] = None
  ): Unit =
    parameters += ParameterOp(name, ParameterLocation.Query, paramType, required, paramDescription)

  def pathParam(
    name: String,
    paramType: TypeRef,
    paramDescription: Option[String] = None
  ): Unit =
    parameters += ParameterOp(name, ParameterLocation.Path, paramType, required = true, paramDescription)

  def headerParam(name: String, paramType: TypeRef, required: Boolean = false): Unit =
    parameters += ParameterOp(name, ParameterLocation.Header, paramType, required)

  def requestBody(
    mediaType: String,
    schemaType: TypeRef,
    required: Boolean = true,
    bodyDescription: Option[String] = None
  ): Unit =
    requestBodies += RequestBodyOp(
      description = bodyDescription,
      required = required,
      mediaTypes = Vector(MediaTypeOp(mediaType, schemaType))
    )

  def response(
    statusCode: Int,
    responseDescription: String,
    mediaType: String = "application/json",
    schemaType: TypeRef = TypeRef.VOID
  ): Unit =
    val mediaTypes =
      if schemaType == TypeRef.VOID then Vector.empty[MediaTypeOp]
      else Vector(MediaTypeOp(mediaType, schemaType))
    responses += ResponseOp(statusCode.toString, responseDescription, mediaTypes = mediaTypes)

  def response(statusCode: String, responseDescription: String): Unit =
    responses += ResponseOp(statusCode, responseDescription)

  def build(): OperationOp = OperationOp(
    httpMethod,
    operationId = Some(operationId),
    summary = summaryText,
    description = descriptionText,
    tags = operationTags.toList,
    deprecated = isDeprecated,
    parameters = parameters.toVector,
    responses = responses.toVector,
    requestBodies = requestBodies.toVector
  )
