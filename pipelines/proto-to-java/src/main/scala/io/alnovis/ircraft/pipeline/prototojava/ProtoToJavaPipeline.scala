package io.alnovis.ircraft.pipeline.prototojava

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.java.emit.DirectJavaEmitter
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.ProtoToCodePipeline

/**
  * Pre-built pipeline: Proto Schema → Java source files.
  *
  * Convenience wrapper around [[ProtoToCodePipeline]] with [[DirectJavaEmitter]].
  */
class ProtoToJavaPipeline(config: LoweringConfig):

  private val delegate = ProtoToCodePipeline(config, DirectJavaEmitter())

  /**
    * Run the full pipeline and emit Java source files.
    *
    * @return
    *   Either errors or a map of file path → source code
    */
  def execute(
    module: Module,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    delegate.execute(module, context)
