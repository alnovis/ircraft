package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.emit.Emitter

trait TargetDialect extends Dialect:
  def emitter: Emitter
