package io.alnovis.ircraft.core

/** An ordered sequence of Passes that transforms a Module through multiple stages.
  *
  * Pipelines:
  *   - Execute passes in order
  *   - Skip disabled passes
  *   - Collect diagnostics from all passes
  *   - Stop on first error (fail-fast) or continue (collect-all)
  */
case class Pipeline(
    name: String,
    passes: Vector[Pass],
    failFast: Boolean = true,
):

  /** Execute the pipeline on the given module. */
  def run(module: Module, context: PassContext): PipelineResult =
    import scala.util.boundary, boundary.break

    var current = module
    var allDiagnostics = List.empty[DiagnosticMessage]
    var passResults = Vector.empty[(String, PassResult)]

    boundary:
      for pass <- passes do
        if !pass.isEnabled(context) then
          context.logger.debug(s"Skipping disabled pass: ${pass.name}")
        else
          context.logger.info(s"Running pass: ${pass.name}")
          val start = System.nanoTime()
          val result = pass.run(current, context)
          val elapsed = (System.nanoTime() - start) / 1_000_000

          context.logger.debug(s"  ${pass.name} completed in ${elapsed}ms")

          allDiagnostics = allDiagnostics ++ result.diagnostics
          passResults = passResults :+ (pass.name, result)

          if result.hasErrors && failFast then
            context.logger.error(s"  ${pass.name} failed with errors, stopping pipeline")
            break(PipelineResult(result.module, allDiagnostics, passResults))
          else current = result.module

      PipelineResult(current, allDiagnostics, passResults)

  /** Append another pass to this pipeline. */
  def andThen(pass: Pass): Pipeline = copy(passes = passes :+ pass)

  /** Compose two pipelines. */
  def andThen(other: Pipeline): Pipeline = copy(passes = passes ++ other.passes)

object Pipeline:
  def apply(name: String, passes: Pass*): Pipeline =
    Pipeline(name, passes.toVector)

  /** Builder for fluent pipeline construction. */
  class Builder(name: String):
    private var passes = Vector.empty[Pass]
    private var failFastMode = true

    def add(pass: Pass): Builder =
      passes = passes :+ pass
      this

    def failFast(enabled: Boolean): Builder =
      failFastMode = enabled
      this

    def build(): Pipeline = Pipeline(name, passes, failFastMode)

  def builder(name: String): Builder = Builder(name)

/** Result of running a full pipeline. */
case class PipelineResult(
    module: Module,
    diagnostics: List[DiagnosticMessage],
    passResults: Vector[(String, PassResult)],
):
  def hasErrors: Boolean = diagnostics.exists(_.isError)
  def isSuccess: Boolean = !hasErrors

  def errors: List[DiagnosticMessage]   = diagnostics.filter(_.isError)
  def warnings: List[DiagnosticMessage] = diagnostics.filter(_.isWarning)
