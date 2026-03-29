package io.alnovis.ircraft.pipeline.prototokotlin

import java.nio.file.Path

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.kotlin.emit.DirectKotlinEmitter
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.GenericProtoToCodePipeline

/**
  * Pre-built pipeline: Proto Schema -> Kotlin source files.
  *
  * Convenience wrapper around [[GenericProtoToCodePipeline]] with [[DirectKotlinEmitter]].
  */
class ProtoToKotlinPipeline(config: LoweringConfig):

  private val delegate = GenericProtoToCodePipeline(config, DirectKotlinEmitter())

  /**
    * Run the full pipeline and emit Kotlin source files.
    *
    * @return
    *   Either errors or a map of file path -> source code
    */
  def execute(
    module: IrModule,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    delegate.execute(module, context)

  def executeIncremental(
    module: IrModule,
    cacheDir: Path,
    context: PassContext = PassContext()
  ): Either[List[DiagnosticMessage], Map[String, String]] =
    delegate.executeIncremental(module, cacheDir, context)
