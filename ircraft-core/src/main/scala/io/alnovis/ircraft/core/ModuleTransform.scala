package io.alnovis.ircraft.core

/**
  * A transformation that combines multiple IR modules into one.
  *
  * Extends the linear Pass model (IrModule -> IrModule) to N inputs:
  * {{{
  * val v1 = lowering.run(protoV1, ctx)
  * val v2 = lowering.run(protoV2, ctx)
  * val merged = myMerge.transform(Vector(v1.module, v2.module), ctx)
  * }}}
  *
  * Use cases: version merge, schema stitching, multi-source composition.
  *
  * Orchestration is plain Scala code -- ModuleTransform does not change Pipeline.
  */
trait ModuleTransform:

  /** Human-readable name for logging/debugging. */
  def name: String

  /** Brief description of what this transform does. */
  def description: String = ""

  /** Combine multiple modules into one. */
  def transform(inputs: Vector[IrModule], context: PassContext): PassResult

object ModuleTransform:

  /** Create a ModuleTransform from a function. No diagnostics. */
  def apply(n: String)(f: Vector[IrModule] => IrModule): ModuleTransform =
    new ModuleTransform:
      val name: String = n
      def transform(inputs: Vector[IrModule], context: PassContext): PassResult =
        PassResult(f(inputs))

  /** Create a binary ModuleTransform from a two-argument function. Most common case. */
  def binary(n: String)(f: (IrModule, IrModule) => IrModule): ModuleTransform =
    apply(n)(modules => f(modules(0), modules(1)))
