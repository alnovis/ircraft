package io.alnovis.ircraft.pipeline.prototojava

import java.nio.file.Path

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.java.emit.DirectJavaEmitter
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.GenericProtoToCodePipeline

/**
  * Pre-built pipeline: Proto Schema -> Java source files.
  *
  * Convenience wrapper around [[GenericProtoToCodePipeline]] with [[DirectJavaEmitter]]. Contains only generic protobuf
  * lowering (no proto-wrapper-specific passes). Domain-specific consumers should compose their own pipeline via
  * `GenericProtoToCodePipeline.pipeline.andThen(...)`.
  */
class ProtoToJavaPipeline(config: LoweringConfig):

  private val delegate = GenericProtoToCodePipeline(config, DirectJavaEmitter())

  /**
    * Run the full pipeline and emit Java source files.
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
