package io.alnovis.ircraft.core

sealed abstract class Severity

object Severity {
  case object Error   extends Severity
  case object Warning extends Severity
  case object Info    extends Severity
}

case class Diagnostic(
  severity: Severity,
  message: String,
  location: Option[String] = None
) {
  def isError: Boolean   = severity == Severity.Error
  def isWarning: Boolean = severity == Severity.Warning
}
