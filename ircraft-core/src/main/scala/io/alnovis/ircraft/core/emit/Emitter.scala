package io.alnovis.ircraft.core.emit

import io.alnovis.ircraft.core.IrModule

/**
  * Base trait for all language emitters.
  *
  * Each dialect provides its own Emitter implementation that transforms a IrModule into source files.
  */
trait Emitter:

  /**
    * Emit a IrModule to source files.
    *
    * @return
    *   Map of file path (relative) -> source code content
    */
  def emit(module: IrModule): Map[String, String]

  /**
    * Emit a IrModule with file-to-entity mapping for incremental generation.
    *
    * @param sourceEntityKey
    *   attribute key to extract entity name from FileOp (None to skip mapping)
    * @return
    *   (file path -> source code, file path -> entity name or None for global files)
    */
  def emitWithMapping(
    module: IrModule,
    sourceEntityKey: Option[String]
  ): (Map[String, String], Map[String, Option[String]]) =
    val files = emit(module)
    (files, files.map((k, _) => k -> None))
