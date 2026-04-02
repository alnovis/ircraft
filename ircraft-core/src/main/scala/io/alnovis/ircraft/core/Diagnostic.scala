package io.alnovis.ircraft.core

enum Severity:
  case Error, Warning, Info

case class Diagnostic(
  severity: Severity,
  message: String,
  location: Option[String] = None
):
  def isError: Boolean   = severity == Severity.Error
  def isWarning: Boolean = severity == Severity.Warning

case class DiagnosticError(diagnostics: Vector[Diagnostic])
  extends RuntimeException(diagnostics.map(_.message).mkString("; "))
