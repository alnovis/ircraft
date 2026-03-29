package io.alnovis.ircraft.pipeline.prototoscala

import java.nio.file.Path

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.scala3.emit.DirectScalaEmitter
import io.alnovis.ircraft.dialect.proto.lowering.LoweringConfig
import io.alnovis.ircraft.dialect.proto.pipeline.GenericProtoToCodePipeline

/**
  * Pre-built pipeline: Proto Schema -> Scala 3 source files.
  *
  * Convenience wrapper around [[GenericProtoToCodePipeline]] with [[DirectScalaEmitter]].
  */
class ProtoToScalaPipeline(config: LoweringConfig):

  private val delegate = GenericProtoToCodePipeline(config, DirectScalaEmitter())

  /**
    * Run the full pipeline and emit Scala 3 source files.
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
