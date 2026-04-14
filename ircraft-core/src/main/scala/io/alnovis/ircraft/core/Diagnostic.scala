package io.alnovis.ircraft.core

/**
  * Severity level for a [[Diagnostic]] message.
  *
  * Defines the three standard severity tiers used throughout the ircraft pipeline:
  * errors that halt processing, warnings that accumulate alongside results, and
  * informational messages.
  *
  * @see [[Diagnostic]] for the primary consumer of severity levels
  * @see [[Outcome]] for how diagnostics are accumulated via `IorT`
  */
sealed abstract class Severity

/**
  * Companion object containing the three severity levels.
  */
object Severity {

  /** An error that prevents further processing of the affected item. */
  case object Error extends Severity

  /** A warning that does not block processing, but should be reported. */
  case object Warning extends Severity

  /** An informational message with no actionable consequence. */
  case object Info extends Severity
}

/**
  * A single diagnostic message produced during an ircraft pipeline pass.
  *
  * Diagnostics carry a [[Severity]] level, a human-readable message, and an
  * optional location string (e.g., a fully-qualified declaration name or source
  * path) to help pinpoint the origin of the issue.
  *
  * Diagnostics are typically accumulated in a `NonEmptyChain[Diagnostic]`
  * within an [[Outcome]] (i.e., `IorT[F, NonEmptyChain[Diagnostic], A]`).
  *
  * {{{
  * val d = Diagnostic(Severity.Error, "Unresolved type: Foo", Some("com.example.Bar"))
  * assert(d.isError)
  * assert(!d.isWarning)
  * }}}
  *
  * @param severity the severity level of this diagnostic
  * @param message  a human-readable description of the issue
  * @param location an optional location identifier (e.g., FQN, file path, or pass name)
  * @see [[Severity]] for the available severity levels
  * @see [[Outcome]] for smart constructors that produce diagnostics
  */
case class Diagnostic(
  severity: Severity,
  message: String,
  location: Option[String] = None
) {

  /**
    * Returns `true` if this diagnostic has [[Severity.Error]] severity.
    *
    * @return `true` when severity is [[Severity.Error]], `false` otherwise
    */
  def isError: Boolean = severity == Severity.Error

  /**
    * Returns `true` if this diagnostic has [[Severity.Warning]] severity.
    *
    * @return `true` when severity is [[Severity.Warning]], `false` otherwise
    */
  def isWarning: Boolean = severity == Severity.Warning
}
