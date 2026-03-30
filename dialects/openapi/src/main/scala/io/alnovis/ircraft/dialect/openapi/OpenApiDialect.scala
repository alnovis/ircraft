package io.alnovis.ircraft.dialect.openapi

import io.alnovis.ircraft.core.*

/** OpenAPI 3.0 specification dialect. */
object OpenApiDialect extends Dialect:

  val namespace: String   = "openapi"
  val description: String = "OpenAPI 3.0 specification representation"

  object Kinds:
    val Spec: NodeKind              = NodeKind(namespace, "spec")
    val SchemaObject: NodeKind      = NodeKind(namespace, "schema_object")
    val SchemaProperty: NodeKind    = NodeKind(namespace, "schema_property")
    val SchemaEnum: NodeKind        = NodeKind(namespace, "schema_enum")
    val SchemaEnumValue: NodeKind   = NodeKind(namespace, "schema_enum_value")
    val SchemaComposition: NodeKind = NodeKind(namespace, "schema_composition")
    val SchemaArray: NodeKind       = NodeKind(namespace, "schema_array")
    val Discriminator: NodeKind     = NodeKind(namespace, "discriminator")
    val Path: NodeKind              = NodeKind(namespace, "path")
    val Operation: NodeKind         = NodeKind(namespace, "operation")
    val Parameter: NodeKind         = NodeKind(namespace, "parameter")
    val RequestBody: NodeKind       = NodeKind(namespace, "request_body")
    val MediaType: NodeKind         = NodeKind(namespace, "media_type")
    val Response: NodeKind          = NodeKind(namespace, "response")
    val Header: NodeKind            = NodeKind(namespace, "header")
    val SecurityScheme: NodeKind    = NodeKind(namespace, "security_scheme")
    val SecurityRequirement: NodeKind = NodeKind(namespace, "security_requirement")
    val Server: NodeKind            = NodeKind(namespace, "server")
    val Tag: NodeKind               = NodeKind(namespace, "tag")
    val Example: NodeKind           = NodeKind(namespace, "example")
    val Link: NodeKind              = NodeKind(namespace, "link")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.Spec, Kinds.SchemaObject, Kinds.SchemaProperty, Kinds.SchemaEnum,
    Kinds.SchemaEnumValue, Kinds.SchemaComposition, Kinds.SchemaArray,
    Kinds.Discriminator, Kinds.Path, Kinds.Operation, Kinds.Parameter,
    Kinds.RequestBody, Kinds.MediaType, Kinds.Response, Kinds.Header,
    Kinds.SecurityScheme, Kinds.SecurityRequirement, Kinds.Server,
    Kinds.Tag, Kinds.Example, Kinds.Link
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to openapi dialect"))
    else Nil
