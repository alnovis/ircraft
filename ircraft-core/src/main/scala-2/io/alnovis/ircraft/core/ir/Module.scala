package io.alnovis.ircraft.core.ir

import cats.Monoid
import io.alnovis.ircraft.core.algebra.Fix

case class Module[D](
  name: String,
  units: Vector[CompilationUnit[D]],
  meta: Meta = Meta.empty
) {
  def mapDecls[E](f: D => E): Module[E] =
    Module(name, units.map(_.mapDecls(f)), meta)
}

object Module {
  def empty[D](name: String): Module[D] = Module(name, Vector.empty)

  implicit def moduleMonoid[D]: Monoid[Module[D]] = new Monoid[Module[D]] {
    def empty: Module[D] = Module("", Vector.empty)
    def combine(x: Module[D], y: Module[D]): Module[D] =
      Module(
        name = if (x.name.isEmpty) y.name else if (y.name.isEmpty) x.name else s"${x.name}+${y.name}",
        units = x.units ++ y.units,
        meta = Monoid[Meta].combine(x.meta, y.meta)
      )
  }
}

case class CompilationUnit[D](
  namespace: String,
  declarations: Vector[D],
  meta: Meta = Meta.empty
) {
  def mapDecls[E](f: D => E): CompilationUnit[E] =
    CompilationUnit(namespace, declarations.map(f), meta)
}
