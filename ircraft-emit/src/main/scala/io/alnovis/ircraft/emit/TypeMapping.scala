package io.alnovis.ircraft.emit

import io.alnovis.ircraft.core.ir.TypeExpr

/**
  * Maps IR type expressions to target-language type name strings and import sets.
  *
  * Each target language provides its own [[TypeMapping]] implementation that knows how to
  * convert abstract `TypeExpr` nodes into concrete language-specific type names.
  * For example, `TypeExpr.Primitive.Int32` becomes `"int"` in Java but `"Int"` in Scala.
  *
  * Implementations must also report the import statements required for each type so that
  * [[ImportCollector]] can gather all necessary imports for a compilation unit.
  *
  * @see [[ImportCollector]] for collecting all imports from a declaration tree
  * @see [[io.alnovis.ircraft.emitters.java.JavaTypeMapping]] for the Java implementation
  * @see [[io.alnovis.ircraft.emitters.scala.ScalaTypeMapping]] for the Scala implementation
  */
trait TypeMapping {

  /**
    * Returns the target-language type name for the given type expression.
    *
    * For primitive types this returns the unboxed name (e.g., `"int"` in Java).
    * For generic containers (lists, maps, etc.), type arguments are included
    * using the language's generic syntax.
    *
    * @param t the IR type expression to convert
    * @return the fully formatted type name string in the target language
    * @throws AssertionError if `t` is an `Unresolved` type that was not resolved by
    *                        earlier pipeline passes
    */
  def typeName(t: TypeExpr): String

  /**
    * Returns the boxed (reference/wrapper) type name for the given type expression.
    *
    * In languages where primitives have separate boxed representations (e.g., Java's
    * `int` vs `Integer`), this method returns the boxed variant. This is required when
    * primitives appear as generic type arguments (e.g., `List<Integer>`).
    *
    * The default implementation delegates to [[typeName]], which is correct for languages
    * like Scala where there is no primitive/boxed distinction.
    *
    * @param t the IR type expression to convert
    * @return the boxed type name string in the target language
    */
  def boxedName(t: TypeExpr): String = typeName(t)

  /**
    * Returns the set of import statements required for the given type expression.
    *
    * For types that are available without imports (e.g., Java primitives, Scala's
    * `Predef` types), this returns an empty set. For named types with a package
    * qualifier, the fully qualified name is returned.
    *
    * @param t the IR type expression to analyze
    * @return the set of fully qualified import paths (e.g., `Set("java.util.List")`)
    */
  def imports(t: TypeExpr): Set[String]

  /**
    * Extracts the simple (unqualified) name from a fully qualified name.
    *
    * @param fqn the fully qualified name (e.g., `"com.example.MyClass"`)
    * @return the simple name (e.g., `"MyClass"`), or the input unchanged if it
    *         contains no dots
    */
  protected def simpleName(fqn: String): String = {
    val dot = fqn.lastIndexOf('.')
    if (dot >= 0) fqn.substring(dot + 1) else fqn
  }

  /**
    * Throws an `AssertionError` indicating an IRCraft pipeline invariant violation.
    *
    * This should be called when the emitter encounters a state that should have been
    * resolved by earlier pipeline passes (e.g., unresolved type references).
    *
    * @param msg the error message describing the invariant violation
    * @throws AssertionError always
    */
  protected def unreachable(msg: String): Nothing =
    throw new AssertionError(s"IRCraft pipeline assertion failed: $msg")
}
