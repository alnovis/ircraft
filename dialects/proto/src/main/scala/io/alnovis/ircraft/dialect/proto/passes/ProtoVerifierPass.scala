package io.alnovis.ircraft.dialect.proto.passes

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*

/** Validates proto dialect IR structural correctness. */
object ProtoVerifierPass extends Pass:

  val name: String        = "proto-verifier"
  val description: String = "Validates proto IR structure"

  def run(module: IrModule, context: PassContext): PassResult =
    val diagnostics = module.topLevel.flatMap(verifyOp).toList
    PassResult(module, diagnostics)

  private def verifyOp(op: Operation): List[DiagnosticMessage] = op match
    case f: ProtoFileOp => verifyFile(f)
    case m: MessageOp   => verifyMessage(m)
    case e: EnumOp      => verifyEnum(e)
    case o: OneofOp     => verifyOneof(o)
    case _: FieldOp | _: EnumValueOp => Nil
    case other =>
      List(DiagnosticMessage.warning(s"Unknown operation type: ${other.qualifiedName}", other.span))

  private def verifyFile(f: ProtoFileOp): List[DiagnosticMessage] =
    f.messages.flatMap(verifyMessage).toList ++ f.enums.flatMap(verifyEnum).toList

  private def verifyMessage(m: MessageOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]

    if m.name.isEmpty then
      diags += DiagnosticMessage.error("Message name must not be empty", m.span)

    // Collect all field numbers (including oneof fields)
    val allFields = m.fields ++ m.oneofs.flatMap(_.fields)
    val numbers = allFields.map(_.number)
    val names = allFields.map(_.name)

    // Check field numbers
    allFields.foreach { f =>
      if f.number <= 0 then
        diags += DiagnosticMessage.error(s"Field '${f.name}' has invalid number ${f.number} (must be > 0)", f.span)
      if f.number >= 19000 && f.number <= 19999 then
        diags += DiagnosticMessage.error(
          s"Field '${f.name}' uses reserved number ${f.number} (19000-19999)",
          f.span
        )
    }

    // Duplicate field numbers
    numbers.groupBy(identity).collect { case (n, occurrences) if occurrences.size > 1 => n }.foreach { n =>
      diags += DiagnosticMessage.error(s"Duplicate field number $n in message '${m.name}'", m.span)
    }

    // Duplicate field names
    names.groupBy(identity).collect { case (n, occurrences) if occurrences.size > 1 => n }.foreach { n =>
      diags += DiagnosticMessage.error(s"Duplicate field name '$n' in message '${m.name}'", m.span)
    }

    // Recurse into nested types
    diags ++= m.nestedMessages.flatMap(verifyMessage)
    diags ++= m.nestedEnums.flatMap(verifyEnum)
    diags ++= m.oneofs.flatMap(verifyOneof)

    diags.result()

  private def verifyEnum(e: EnumOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]

    if e.name.isEmpty then
      diags += DiagnosticMessage.error("Enum name must not be empty", e.span)

    if e.values.isEmpty then
      diags += DiagnosticMessage.warning(s"Enum '${e.name}' has no values", e.span)

    // Duplicate value numbers
    e.values.map(_.number).groupBy(identity).collect { case (n, occ) if occ.size > 1 => n }.foreach { n =>
      diags += DiagnosticMessage.error(s"Duplicate enum value number $n in enum '${e.name}'", e.span)
    }

    diags.result()

  private def verifyOneof(o: OneofOp): List[DiagnosticMessage] =
    val diags = List.newBuilder[DiagnosticMessage]

    if o.name.isEmpty then
      diags += DiagnosticMessage.error("Oneof name must not be empty", o.span)

    if o.fields.isEmpty then
      diags += DiagnosticMessage.warning(s"Oneof '${o.name}' has no fields", o.span)

    diags.result()
