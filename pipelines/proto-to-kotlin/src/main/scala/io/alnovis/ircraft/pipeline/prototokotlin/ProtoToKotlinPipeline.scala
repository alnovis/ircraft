package io.alnovis.ircraft.pipeline.prototokotlin

import java.nio.file.Path

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.kotlin.emit.DirectKotlinEmitter
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.ProtoToCodePipeline

/**
  * Pre-built pipeline: Proto Schema -> Kotlin source files.
  *
  * Convenience wrapper around [[ProtoToCodePipeline]] with [[DirectKotlinEmitter]].
  */
class ProtoToKotlinPipeline(config: LoweringConfig):

  private val delegate = ProtoToCodePipeline(config, DirectKotlinEmitter())

  /**
    * Run the full pipeline and emit Kotlin source files.
    *
    * @return
    *   Either errors or a map of file path -> source code
    */
  def execute(
    module: Module,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    delegate.execute(module, context)

  def executeIncremental(
    module: Module,
    cacheDir: Path,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    delegate.executeIncremental(module, cacheDir, context)
