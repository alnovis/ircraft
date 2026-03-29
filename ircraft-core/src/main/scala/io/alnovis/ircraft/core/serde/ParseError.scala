package io.alnovis.ircraft.core.serde

case class ParseError(message: String, line: Int, column: Int):
  override def toString: String = s"Parse error at $line:$column: $message"
