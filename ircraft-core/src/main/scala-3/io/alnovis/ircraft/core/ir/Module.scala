package io.alnovis.ircraft.core.ir

import cats.Monoid
import io.alnovis.ircraft.core.algebra.Fix

/**
  * A compilation module containing one or more [[CompilationUnit]]s.
  *
  * A `Module` is the top-level container in the ircraft IR pipeline.
  * It groups compilation units under a common name and carries module-level
  * [[Meta]] metadata (e.g., pass trace information).
  *
  * The type parameter `D` represents the declaration type, which is typically
  * [[io.alnovis.ircraft.core.algebra.Fix]][[[SemanticF]]] for the core IR, or
  * `Fix[ProtoF :+: SemanticF]` for a composed IR with dialect extensions.
  *
  * @tparam D the declaration (node) type stored in compilation units
  * @param name  the module name (e.g., a file name or logical grouping)
  * @param units the compilation units belonging to this module
  * @param meta  module-level metadata
  * @see [[CompilationUnit]] for the namespace-level container
  * @see [[SemanticModule]] for the standard semantic module type alias
  */
case class Module[D](
  name: String,
  units: Vector[CompilationUnit[D]],
  meta: Meta = Meta.empty
):

  /**
    * Transforms all declarations in every compilation unit using the given function.
    *
    * @tparam E the output declaration type
    * @param f the transformation function to apply to each declaration
    * @return a new module with all declarations mapped through `f`
    */
  def mapDecls[E](f: D => E): Module[E] =
    Module(name, units.map(_.mapDecls(f)), meta)

/**
  * Companion object for [[Module]] providing factory methods and a `cats.Monoid` instance.
  */
object Module:

  /**
    * Creates an empty module with the given name and no compilation units.
    *
    * @tparam D the declaration type
    * @param name the module name
    * @return an empty module
    */
  def empty[D](name: String): Module[D] = Module(name, Vector.empty)

  /**
    * `cats.Monoid` instance for [[Module]].
    *
    * Combining two modules concatenates their compilation units and merges
    * their metadata. Module names are combined with `+` if both are non-empty;
    * otherwise the non-empty name is preserved.
    *
    * @tparam D the declaration type
    */
  given moduleMonoid[D]: Monoid[Module[D]] with
    def empty: Module[D] = Module("", Vector.empty)

    def combine(x: Module[D], y: Module[D]): Module[D] =
      Module(
        name = if x.name.isEmpty then y.name else if y.name.isEmpty then x.name else s"${x.name}+${y.name}",
        units = x.units ++ y.units,
        meta = Monoid[Meta].combine(x.meta, y.meta)
      )

/**
  * A namespace-scoped container for declarations within a [[Module]].
  *
  * Each `CompilationUnit` groups declarations under a single namespace
  * (package, module path, etc.) and may carry unit-level [[Meta]] metadata.
  *
  * @tparam D the declaration (node) type
  * @param namespace    the namespace (package/module path) for these declarations
  * @param declarations the declarations in this compilation unit
  * @param meta         unit-level metadata
  */
case class CompilationUnit[D](
  namespace: String,
  declarations: Vector[D],
  meta: Meta = Meta.empty
):

  /**
    * Transforms all declarations in this unit using the given function.
    *
    * @tparam E the output declaration type
    * @param f the transformation function to apply to each declaration
    * @return a new compilation unit with declarations mapped through `f`
    */
  def mapDecls[E](f: D => E): CompilationUnit[E] =
    CompilationUnit(namespace, declarations.map(f), meta)

/**
  * Convenience type alias for a module containing standard semantic IR declarations.
  *
  * Equivalent to `Module[Fix[SemanticF]]`.
  *
  * @see [[Module]] for the generic module container
  * @see [[SemanticF]] for the standard dialect functor
  * @see [[io.alnovis.ircraft.core.algebra.Fix]] for the fixpoint wrapper
  */
type SemanticModule = Module[Fix[SemanticF]]
