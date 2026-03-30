package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*

trait SourceDialect extends Dialect:
  def loweringPass: Lowering
  def verifierPass: Option[Pass] = None
  final def standardPipeline: Pipeline =
    val passes = verifierPass.toVector :+ loweringPass
    Pipeline(s"$namespace-pipeline", passes)
