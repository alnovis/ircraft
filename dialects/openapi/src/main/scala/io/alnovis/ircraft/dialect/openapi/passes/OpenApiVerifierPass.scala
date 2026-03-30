package io.alnovis.ircraft.dialect.openapi.passes

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.ops.*

/** Validates OpenAPI dialect IR structural correctness. */
object OpenApiVerifierPass extends Pass:

  val name: String        = "openapi-verifier"
  val description: String = "Validates OpenAPI IR structure"

  def run(module: IrModule, context: PassContext): PassResult =
    val diagnostics = module.topLevel.flatMap(verifyOp).toList
    PassResult(module, diagnostics)

  private def verifyOp(op: Operation): List[DiagnosticMessage] = op match
    case s: OpenApiSpecOp       => verifySpec(s)
    case s: SchemaObjectOp      => verifySchemaObject(s)
    case e: SchemaEnumOp        => verifySchemaEnum(e)
    case c: SchemaCompositionOp => verifyComposition(c)
    case p: PathOp              => verifyPath(p)
    case o: OperationOp         => verifyOperation(o)
    case p: ParameterOp         => verifyParameter(p)
    case _: SchemaPropertyOp | _: SchemaEnumValueOp | _: SchemaArrayOp |
         _: DiscriminatorOp | _: RequestBodyOp | _: MediaTypeOp |
         _: ResponseOp | _: HeaderOp | _: SecuritySchemeOp |
         _: SecurityRequirementOp | _: ServerOp | _: TagOp |
         _: ExampleOp | _: LinkOp =>
      Nil
    case other =>
      List(DiagnosticMessage.warning(s"Unknown operation type: ${other.qualifiedName}", other.span))

  private def verifySpec(s: OpenApiSpecOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    diags ++= s.schemas.flatMap(verifyOp)
    diags ++= s.paths.flatMap(verifyPath)
    diags.result()

  private def verifySchemaObject(s: SchemaObjectOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if s.name.isEmpty then
      diags += DiagnosticMessage.error("Schema object name must not be empty", s.span)
    diags.result()

  private def verifySchemaEnum(e: SchemaEnumOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if e.name.isEmpty then
      diags += DiagnosticMessage.error("Schema enum name must not be empty", e.span)
    if e.values.isEmpty then
      diags += DiagnosticMessage.error(s"Schema enum '${e.name}' must have at least one value", e.span)
    diags.result()

  private def verifyComposition(c: SchemaCompositionOp): List[DiagnosticMessage] =
    c.schemas.flatMap(verifyOp).toList

  private def verifyPath(p: PathOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if p.pathPattern.isEmpty then
      diags += DiagnosticMessage.error("Path pattern must not be empty", p.span)
    diags ++= p.operations.flatMap(verifyOperation)
    diags.result()

  private def verifyOperation(o: OperationOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if o.responses.isEmpty then
      diags += DiagnosticMessage.error(
        s"Operation '${o.operationId.getOrElse("<unnamed>")}' must have at least one response",
        o.span
      )
    diags ++= o.parameters.flatMap(verifyParameter)
    diags.result()

  private def verifyParameter(p: ParameterOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if p.location == ParameterLocation.Path && !p.required then
      diags += DiagnosticMessage.error(
        s"Path parameter '${p.name}' must be required",
        p.span
      )
    diags.result()
