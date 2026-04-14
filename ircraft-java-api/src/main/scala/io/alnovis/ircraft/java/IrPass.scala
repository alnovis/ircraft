package io.alnovis.ircraft.java

/**
  * A transformation pass that takes an {@link IrModule} and produces a {@link Result} containing
  * a (possibly modified) {@code IrModule}.
  *
  * <p>This is the Java-friendly replacement for the Scala {@code Kleisli[F, Module, Module]}
  * pattern used in ircraft core. Passes can be composed sequentially using
  * {@link #andThen}, and errors short-circuit the pipeline while warnings accumulate.</p>
  *
  * <p>{@code IrPass} is a functional interface, so it can be implemented as a lambda in Java.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * // Create a pass using a lambda
  * IrPass renamePass = module -> {
  *     // transform the module...
  *     return Result.ok(transformedModule);
  * };
  *
  * // Create a pure pass (no diagnostics possible)
  * IrPass upper = IrPass.pure("uppercase", module -> {
  *     // simple transformation...
  *     return transformedModule;
  * });
  *
  * // Compose passes
  * IrPass pipeline = renamePass.andThen(upper);
  * Result<IrModule> result = pipeline.apply(inputModule);
  * }}}
  *
  * @see [[IrPipeline]]  for composing and running multiple passes
  * @see [[Result]]       for the three-state result type
  * @see [[IrModule]]     for the data being transformed
  */
@FunctionalInterface
trait IrPass {

  /**
    * Applies this pass to the given module.
    *
    * @param module the input module to transform
    * @return a {@link Result} containing the transformed module, possibly with
    *         warnings or errors
    */
  def apply(module: IrModule): Result[IrModule]

  /**
    * Composes this pass with another, running this pass first, then {@code next}.
    *
    * <p>Semantics:</p>
    * <ul>
    *   <li>If this pass returns an error, {@code next} is not executed and the error propagates.</li>
    *   <li>If both passes succeed, warnings from both are accumulated in the result.</li>
    * </ul>
    *
    * @param next the pass to run after this one
    * @return a new composed pass
    */
  def andThen(next: IrPass): IrPass = { module =>
    val r = apply(module)
    if (r.isError) r
    else {
      val r2 = next.apply(r.value)
      // accumulate warnings from both passes
      if (r.hasWarnings && r2.isSuccess) {
        val allDiags = new java.util.ArrayList(r.diagnostics)
        allDiags.addAll(r2.diagnostics)
        if (r2.isError) r2
        else Result.withWarnings(r2.value, allDiags)
      } else r2
    }
  }
}

/**
  * Factory methods for creating common {@link IrPass} instances.
  *
  * @see [[IrPass]]
  */
object IrPass {

  /**
    * Creates a pure pass that always succeeds with no diagnostics.
    *
    * <p>Use this when your transformation cannot fail and produces no warnings.
    * The {@code name} parameter is for documentation/debugging purposes only.</p>
    *
    * <h3>Usage from Java</h3>
    * {{{
    * IrPass noOp = IrPass.pure("no-op", module -> module);
    * }}}
    *
    * @param name a human-readable name for this pass (for debugging)
    * @param f    the transformation function
    * @return a new pass that wraps the function's result in {@code Result.ok}
    */
  def pure(name: String, f: java.util.function.Function[IrModule, IrModule]): IrPass =
    module => Result.ok(f.apply(module))

  /**
    * Returns the identity pass that returns the input module unchanged.
    *
    * <p>Useful as a default or starting point for pipeline composition.</p>
    *
    * @return the identity pass
    */
  def identity: IrPass = module => Result.ok(module)
}

/**
  * Utilities for composing and running {@link IrPass} pipelines.
  *
  * <h3>Usage from Java</h3>
  * {{{
  * IrPass pass1 = IrPass.pure("step1", m -> transform1(m));
  * IrPass pass2 = IrPass.pure("step2", m -> transform2(m));
  * IrPass pass3 = IrPass.pure("step3", m -> transform3(m));
  *
  * IrPass pipeline = IrPipeline.of(pass1, pass2, pass3);
  * Result<IrModule> result = IrPipeline.run(pipeline, inputModule);
  *
  * if (result.isSuccess()) {
  *     IrModule output = result.value();
  * }
  * }}}
  *
  * @see [[IrPass]]
  */
object IrPipeline {

  /**
    * Composes one or more passes into a single sequential pipeline.
    *
    * <p>Passes are executed left to right. Errors short-circuit; warnings accumulate.</p>
    *
    * @param first the first pass in the pipeline
    * @param rest  additional passes to execute after the first
    * @return a single composed pass representing the entire pipeline
    */
  def of(first: IrPass, rest: IrPass*): IrPass =
    rest.foldLeft(first)(_.andThen(_))

  /**
    * Runs a pipeline pass against the given module.
    *
    * <p>This is a convenience method equivalent to {@code pipeline.apply(module)}.</p>
    *
    * @param pipeline the pass (or composed pipeline) to execute
    * @param module   the input module
    * @return the result of applying the pipeline
    */
  def run(pipeline: IrPass, module: IrModule): Result[IrModule] =
    pipeline.apply(module)
}
