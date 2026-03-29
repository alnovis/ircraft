package io.alnovis.ircraft.dialect.proto.passes

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*

/** Validates that a IrModule contains well-formed proto dialect operations. */
object ProtoVerifierPass extends Pass:

  val name: String        = "proto-verifier"
  val description: String = "Validates proto dialect IR for structural correctness"

  def run(module: IrModule, context: PassContext): PassResult =
    val diagnostics = module.topLevel.flatMap(verifyOp).toList
    PassResult(module, diagnostics)

  private def verifyOp(op: Operation): List[DiagnosticMessage] = op match
    case s: SchemaOp       => verifySchema(s)
    case m: MessageOp      => verifyMessage(m)
    case f: FieldOp        => verifyField(f)
    case e: EnumOp         => verifyEnum(e)
    case v: EnumValueOp    => verifyEnumValue(v)
    case o: OneofOp        => verifyOneof(o)
    case c: ConflictEnumOp => verifyConflictEnum(c)
    case other => List(DiagnosticMessage.warning(s"Unverified operation type: ${other.qualifiedName}", other.span))

  private def verifySchema(s: SchemaOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if s.versions.isEmpty then diags += DiagnosticMessage.error("Schema must have at least one version", s.span)
    if s.versions.distinct.size != s.versions.size then
      diags += DiagnosticMessage.error("Schema versions must be unique", s.span)
    diags ++= s.messages.flatMap(verifyMessage)
    diags ++= s.enums.flatMap(verifyEnum)
    diags.result()

  private def verifyMessage(m: MessageOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if m.name.isEmpty then diags += DiagnosticMessage.error("Message name must not be empty", m.span)
    if m.presentInVersions.isEmpty then
      diags += DiagnosticMessage.error(s"Message '${m.name}' must be present in at least one version", m.span)

    // Check for duplicate field numbers
    val numbers = m.fields.map(_.number)
    if numbers.distinct.size != numbers.size then
      diags += DiagnosticMessage.error(s"Message '${m.name}' has duplicate field numbers", m.span)

    // Check for duplicate field names
    val names = m.fields.map(_.name)
    if names.distinct.size != names.size then
      diags += DiagnosticMessage.error(s"Message '${m.name}' has duplicate field names", m.span)

    diags ++= m.fields.flatMap(verifyField)
    diags ++= m.oneofs.flatMap(verifyOneof)
    diags ++= m.nestedMessages.flatMap(verifyMessage)
    diags ++= m.nestedEnums.flatMap(verifyEnum)
    diags.result()

  private def verifyField(f: FieldOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if f.name.isEmpty then diags += DiagnosticMessage.error("Field name must not be empty", f.span)
    if f.number <= 0 then diags += DiagnosticMessage.error(s"Field '${f.name}' must have a positive number", f.span)
    if f.presentInVersions.isEmpty then
      diags += DiagnosticMessage.error(s"Field '${f.name}' must be present in at least one version", f.span)
    diags.result()

  private def verifyEnum(e: EnumOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if e.name.isEmpty then diags += DiagnosticMessage.error("Enum name must not be empty", e.span)
    if e.presentInVersions.isEmpty then
      diags += DiagnosticMessage.error(s"Enum '${e.name}' must be present in at least one version", e.span)

    val numbers = e.values.map(_.number)
    if numbers.distinct.size != numbers.size then
      diags += DiagnosticMessage.error(s"Enum '${e.name}' has duplicate value numbers", e.span)

    diags ++= e.values.flatMap(verifyEnumValue)
    diags.result()

  private def verifyEnumValue(v: EnumValueOp): List[DiagnosticMessage] =
    if v.name.isEmpty then List(DiagnosticMessage.error("Enum value name must not be empty", v.span))
    else Nil

  private def verifyOneof(o: OneofOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if o.protoName.isEmpty then diags += DiagnosticMessage.error("Oneof name must not be empty", o.span)
    if o.fields.isEmpty then
      diags += DiagnosticMessage.error(s"Oneof '${o.protoName}' must have at least one field", o.span)
    diags ++= o.fields.flatMap(verifyField)
    diags.result()

  private def verifyConflictEnum(c: ConflictEnumOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]
    if c.enumName.isEmpty then diags += DiagnosticMessage.error("Conflict enum name must not be empty", c.span)
    if c.fieldName.isEmpty then diags += DiagnosticMessage.error("Conflict enum field name must not be empty", c.span)
    diags.result()
