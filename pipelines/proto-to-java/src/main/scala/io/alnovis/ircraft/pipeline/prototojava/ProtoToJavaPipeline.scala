package io.alnovis.ircraft.pipeline.prototojava

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.Emitter
import io.alnovis.ircraft.dialect.java.emit.DirectJavaEmitter
import io.alnovis.ircraft.dialect.proto.lowering.{LoweringConfig, ProtoToSemanticLowering}
import io.alnovis.ircraft.dialect.proto.passes.ProtoVerifierPass

/**
  * Pre-built pipeline: Proto Schema → Java source files.
  *
  * Composes: ProtoVerifierPass → ProtoToSemanticLowering → DirectJavaEmitter
  */
class ProtoToJavaPipeline(config: LoweringConfig):

  private val pipeline: Pipeline = Pipeline(
    "proto-to-java",
    ProtoVerifierPass,
    ProtoToSemanticLowering(config)
  )

  private val emitter: Emitter = DirectJavaEmitter()

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
    val result = pipeline.run(module, context)
    if result.hasErrors then Left(result.errors)
    else Right(emitter.emit(result.module))
