package io.alnovis.ircraft.pipeline.prototoscala

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.scala3.emit.DirectScalaEmitter
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.ProtoToCodePipeline

/**
  * Pre-built pipeline: Proto Schema -> Scala 3 source files.
  *
  * Convenience wrapper around [[ProtoToCodePipeline]] with [[DirectScalaEmitter]].
  */
class ProtoToScalaPipeline(config: LoweringConfig):

  private val delegate = ProtoToCodePipeline(config, DirectScalaEmitter())

  /**
    * Run the full pipeline and emit Scala 3 source files.
    *
    * @return
    *   Either errors or a map of file path -> source code
    */
  def execute(
    module: Module,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    delegate.execute(module, context)
