package io.alnovis.ircraft.core

/**
  * A single transformation pass in the compilation pipeline (Nanopass concept).
  *
  * Each pass performs one focused transformation:
  *   - Has well-defined input and output (Module -> PassResult)
  *   - Is stateless (all state in PassContext)
  *   - Can be tested in isolation
  *   - Can be composed into Pipelines
  *
  * @see
  *   [[https://nanopass.org/ Nanopass Framework]]
  */
trait Pass:

  /** Human-readable name for logging/debugging. */
  def name: String

  /** Brief description of what this pass does. */
  def description: String

  /** Apply this pass to the module. */
  def run(module: Module, context: PassContext): PassResult

  /** Whether this pass is enabled for the given context. Allows conditional passes. */
  def isEnabled(context: PassContext): Boolean = true

/** A Lowering is a Pass that transforms operations from a higher-level dialect to a lower-level dialect. */
trait Lowering extends Pass:

  /** Source dialect (higher level). */
  def sourceDialect: Dialect

  /** Target dialect (lower level). */
  def targetDialect: Dialect

/** Result of running a Pass. */
case class PassResult(
  module: Module,
  diagnostics: List[DiagnosticMessage] = Nil
):
  def hasErrors: Boolean = diagnostics.exists(_.isError)
  def isSuccess: Boolean = !hasErrors

/** Context passed to passes during pipeline execution. */
case class PassContext(
  config: Map[String, String] = Map.empty,
  logger: PassLogger = PassLogger.noop
):
  def get(key: String): Option[String]                = config.get(key)
  def getOrElse(key: String, default: String): String = config.getOrElse(key, default)
  def getBool(key: String): Boolean                   = config.get(key).exists(_.toBoolean)

/** Simple logger interface for pass execution. */
trait PassLogger:
  def info(msg: String): Unit
  def warn(msg: String): Unit
  def error(msg: String): Unit
  def debug(msg: String): Unit

object PassLogger:

  val noop: PassLogger = new PassLogger:
    def info(msg: String): Unit  = ()
    def warn(msg: String): Unit  = ()
    def error(msg: String): Unit = ()
    def debug(msg: String): Unit = ()

  val console: PassLogger = new PassLogger:
    def info(msg: String): Unit  = println(s"[INFO]  $msg")
    def warn(msg: String): Unit  = println(s"[WARN]  $msg")
    def error(msg: String): Unit = println(s"[ERROR] $msg")
    def debug(msg: String): Unit = println(s"[DEBUG] $msg")
