package io.alnovis.ircraft.core

/**
  * An ordered sequence of Passes that transforms a Module through multiple stages.
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
  failFast: Boolean = true
):

  /** Execute the pipeline on the given module. */
  def run(module: Module, context: PassContext): PipelineResult =
    case class State(module: Module, diagnostics: List[DiagnosticMessage], results: Vector[(String, PassResult)])

    val init          = State(module, Nil, Vector.empty)
    val enabledPasses = passes.filter(_.isEnabled(context))
    passes.filterNot(_.isEnabled(context)).foreach(p => context.logger.debug(s"Skipping disabled pass: ${p.name}"))

    enabledPasses.foldLeft(init): (state, pass) =>
      if state.diagnostics.exists(_.isError) && failFast then state
      else
        context.logger.info(s"Running pass: ${pass.name}")
        val start   = System.nanoTime()
        val result  = pass.run(state.module, context)
        val elapsed = (System.nanoTime() - start) / 1_000_000
        context.logger.debug(s"  ${pass.name} completed in ${elapsed}ms")

        val next = State(
          if result.hasErrors then state.module else result.module,
          state.diagnostics ++ result.diagnostics,
          state.results :+ (pass.name, result)
        )

        if result.hasErrors && failFast then
          context.logger.error(s"  ${pass.name} failed with errors, stopping pipeline")
        next
    match
      case State(m, d, r) => PipelineResult(m, d, r)

  /** Append another pass to this pipeline. */
  def andThen(pass: Pass): Pipeline = copy(passes = passes :+ pass)

  /** Compose two pipelines. */
  def andThen(other: Pipeline): Pipeline = copy(passes = passes ++ other.passes)

object Pipeline:

  def apply(name: String, passes: Pass*): Pipeline =
    Pipeline(name, passes.toVector)

  /** Builder for fluent pipeline construction. */
  class Builder(name: String):
    private var passes       = Vector.empty[Pass]
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
  passResults: Vector[(String, PassResult)]
):
  def hasErrors: Boolean = diagnostics.exists(_.isError)
  def isSuccess: Boolean = !hasErrors

  def errors: List[DiagnosticMessage]   = diagnostics.filter(_.isError)
  def warnings: List[DiagnosticMessage] = diagnostics.filter(_.isWarning)
