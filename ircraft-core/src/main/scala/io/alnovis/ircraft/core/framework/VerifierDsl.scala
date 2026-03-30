package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*

object VerifierDsl:

  def nameNotEmpty(op: Operation, name: String): List[DiagnosticMessage] =
    if name.trim.isEmpty then
      List(DiagnosticMessage.error(s"${op.qualifiedName} has empty name", op.span))
    else Nil

  def fieldNotEmpty(value: String, label: String, span: Option[Span]): List[DiagnosticMessage] =
    if value.trim.isEmpty then
      List(DiagnosticMessage.error(s"$label must not be empty", span))
    else Nil

  def noDuplicates[A, K](
    items: Iterable[A],
    key: A => K,
    label: String,
    context: String,
    span: Option[Span]
  ): List[DiagnosticMessage] =
    val dupes = items.groupBy(key).collect { case (k, vs) if vs.size > 1 => k }
    dupes.map: k =>
      DiagnosticMessage.error(s"Duplicate $label '$k' in $context", span)
    .toList

  def nonEmpty(
    items: Iterable[?],
    label: String,
    span: Option[Span],
    severity: Severity = Severity.Error
  ): List[DiagnosticMessage] =
    if items.isEmpty then
      List(DiagnosticMessage(severity, s"$label must not be empty", span))
    else Nil

  def positive(value: Int, label: String, span: Option[Span]): List[DiagnosticMessage] =
    if value <= 0 then
      List(DiagnosticMessage.error(s"$label must be positive, got $value", span))
    else Nil

  def notInReservedRange(
    value: Int,
    rangeStart: Int,
    rangeEnd: Int,
    label: String,
    span: Option[Span]
  ): List[DiagnosticMessage] =
    if value >= rangeStart && value <= rangeEnd then
      List(DiagnosticMessage.error(s"$label value $value is in reserved range [$rangeStart, $rangeEnd]", span))
    else Nil

  def verifierPass(passName: String, passDescription: String = "")(
    verifyOp: Operation => List[DiagnosticMessage]
  ): Pass =
    new Pass:
      val name: String        = passName
      val description: String = passDescription

      def run(module: IrModule, context: PassContext): PassResult =
        val diagnostics = module.topLevel.flatMap(verifyAll(_, verifyOp)).toList
        PassResult(module, diagnostics)

  private def verifyAll(
    op: Operation,
    verifyOp: Operation => List[DiagnosticMessage]
  ): List[DiagnosticMessage] =
    val self     = verifyOp(op)
    val children = op.children.flatMap(child => verifyAll(child, verifyOp)).toList
    self ++ children
