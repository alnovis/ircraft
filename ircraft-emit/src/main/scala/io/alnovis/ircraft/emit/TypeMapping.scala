package io.alnovis.ircraft.emit

import io.alnovis.ircraft.core.ir.TypeExpr

trait TypeMapping {
  def typeName(t: TypeExpr): String
  def boxedName(t: TypeExpr): String = typeName(t)
  def imports(t: TypeExpr): Set[String]

  protected def simpleName(fqn: String): String = {
    val dot = fqn.lastIndexOf('.')
    if (dot >= 0) fqn.substring(dot + 1) else fqn
  }

  protected def unreachable(msg: String): Nothing =
    throw new AssertionError(s"IRCraft pipeline assertion failed: $msg")
}
