package io.alnovis.ircraft.core.ir

import cats.Monoid

case class Module(
  name: String,
  units: Vector[CompilationUnit],
  meta: Meta = Meta.empty
)

object Module:
  def empty(name: String): Module = Module(name, Vector.empty)

  given Monoid[Module] with
    def empty: Module = Module("", Vector.empty)
    def combine(x: Module, y: Module): Module =
      Module(
        name = if x.name.isEmpty then y.name else if y.name.isEmpty then x.name else s"${x.name}+${y.name}",
        units = x.units ++ y.units,
        meta = Monoid[Meta].combine(x.meta, y.meta)
      )

case class CompilationUnit(
  namespace: String,
  declarations: Vector[Decl],
  meta: Meta = Meta.empty
)
