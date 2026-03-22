package io.alnovis.ircraft.core

/** Severity level for diagnostic messages. */
enum Severity:
  case Info, Warning, Error

/** A diagnostic message produced during verification or transformation. */
case class DiagnosticMessage(
  severity: Severity,
  message: String,
  span: Option[Span] = None
):
  def isError: Boolean   = severity == Severity.Error
  def isWarning: Boolean = severity == Severity.Warning

  override def toString: String =
    val loc = span.map(s => s" at $s").getOrElse("")
    s"[$severity]$loc $message"

object DiagnosticMessage:

  def error(msg: String, span: Option[Span] = None): DiagnosticMessage =
    DiagnosticMessage(Severity.Error, msg, span)

  def warning(msg: String, span: Option[Span] = None): DiagnosticMessage =
    DiagnosticMessage(Severity.Warning, msg, span)

  def info(msg: String, span: Option[Span] = None): DiagnosticMessage =
    DiagnosticMessage(Severity.Info, msg, span)
