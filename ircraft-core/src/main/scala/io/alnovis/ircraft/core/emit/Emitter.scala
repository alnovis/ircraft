package io.alnovis.ircraft.core.emit

import io.alnovis.ircraft.core.Module

/**
  * Base trait for all language emitters.
  *
  * Each dialect provides its own Emitter implementation that transforms a Module into source files.
  */
trait Emitter:

  /**
    * Emit a Module to source files.
    *
    * @return
    *   Map of file path (relative) → source code content
    */
  def emit(module: Module): Map[String, String]
