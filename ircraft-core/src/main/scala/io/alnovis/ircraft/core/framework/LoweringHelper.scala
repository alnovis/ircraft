package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*

abstract class LoweringHelper extends Lowering:
  protected def lowerTopLevel(op: Operation, context: PassContext): Option[Vector[Operation]]

  final def run(module: IrModule, context: PassContext): PassResult =
    val newTopLevel = module.topLevel.flatMap: op =>
      lowerTopLevel(op, context).getOrElse(Vector(op))
    PassResult(IrModule(module.name, newTopLevel, module.attributes, module.span))
