package io.alnovis.ircraft.core.ir

import cats.Monoid
import io.alnovis.ircraft.core.algebra.Fix

case class Module[D](
  name: String,
  units: Vector[CompilationUnit[D]],
  meta: Meta = Meta.empty
):

  def mapDecls[E](f: D => E): Module[E] =
    Module(name, units.map(_.mapDecls(f)), meta)

object Module:
  def empty[D](name: String): Module[D] = Module(name, Vector.empty)

  given moduleMonoid[D]: Monoid[Module[D]] with
    def empty: Module[D] = Module("", Vector.empty)

    def combine(x: Module[D], y: Module[D]): Module[D] =
      Module(
        name = if x.name.isEmpty then y.name else if y.name.isEmpty then x.name else s"${x.name}+${y.name}",
        units = x.units ++ y.units,
        meta = Monoid[Meta].combine(x.meta, y.meta)
      )

case class CompilationUnit[D](
  namespace: String,
  declarations: Vector[D],
  meta: Meta = Meta.empty
):

  def mapDecls[E](f: D => E): CompilationUnit[E] =
    CompilationUnit(namespace, declarations.map(f), meta)

/** Convenience type alias for the standard semantic module. */
type SemanticModule = Module[Fix[SemanticF]]
