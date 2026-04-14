package io.alnovis.ircraft.core.ir

import cats.Monoid
import io.alnovis.ircraft.core.algebra.Fix

/**
  * A module representing a named collection of compilation units.
  *
  * Module is the top-level container in the ircraft IR. It groups
  * [[CompilationUnit]]s together under a common name and carries
  * module-level [[Meta]] (e.g., pass trace information).
  *
  * The type parameter `D` represents the declaration type. In fixed-point
  * form this is typically `Fix[SemanticF]` or `Fix[Coproduct[ProtoF, SemanticF, *]]`.
  *
  * @param name  the module name (e.g., derived from the input schema file)
  * @param units the compilation units within this module
  * @param meta  extensible metadata map for module-level annotations
  * @tparam D the declaration type
  * @see [[CompilationUnit]]
  * @see [[Meta]]
  */
case class Module[D](
  name: String,
  units: Vector[CompilationUnit[D]],
  meta: Meta = Meta.empty
) {

  /**
    * Transforms all declarations in this module by applying the given function.
    *
    * @param f the mapping function to apply to each declaration
    * @tparam E the target declaration type
    * @return a new [[Module]] with transformed declarations
    */
  def mapDecls[E](f: D => E): Module[E] =
    Module(name, units.map(_.mapDecls(f)), meta)
}

/**
  * Companion object for [[Module]] providing factory methods and
  * implicit typeclass instances.
  */
object Module {

  /**
    * Creates an empty module with the given name and no compilation units.
    *
    * @param name the module name
    * @tparam D the declaration type
    * @return an empty [[Module]]
    */
  def empty[D](name: String): Module[D] = Module(name, Vector.empty)

  /**
    * Implicit [[cats.Monoid]] instance for [[Module]].
    *
    * Combining two modules concatenates their names (with "+" separator),
    * appends their compilation unit vectors, and merges their metadata.
    *
    * @tparam D the declaration type
    * @return a [[cats.Monoid]] for `Module[D]`
    */
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

/**
  * A compilation unit representing a namespace and its declarations.
  *
  * Each compilation unit corresponds to a single namespace (package)
  * and contains a vector of declarations.
  *
  * @param namespace    the fully qualified namespace (package) for this unit
  * @param declarations the declarations within this compilation unit
  * @param meta         extensible metadata map
  * @tparam D the declaration type
  * @see [[Module]]
  */
case class CompilationUnit[D](
  namespace: String,
  declarations: Vector[D],
  meta: Meta = Meta.empty
) {

  /**
    * Transforms all declarations in this compilation unit by applying the given function.
    *
    * @param f the mapping function to apply to each declaration
    * @tparam E the target declaration type
    * @return a new [[CompilationUnit]] with transformed declarations
    */
  def mapDecls[E](f: D => E): CompilationUnit[E] =
    CompilationUnit(namespace, declarations.map(f), meta)
}
