package io.alnovis.ircraft.core.ir

case class Module(
  name: String,
  units: Vector[CompilationUnit],
  meta: Meta = Meta.empty
)

object Module:
  def empty(name: String): Module = Module(name, Vector.empty)

case class CompilationUnit(
  namespace: String,
  declarations: Vector[Decl],
  meta: Meta = Meta.empty
)
