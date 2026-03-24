package io.alnovis.ircraft.core

/**
  * Maps ircraft TypeRef to language-specific type strings.
  *
  * Each language dialect implements this trait to provide type name resolution, boxing (for generics), and import
  * collection.
  */
trait LanguageTypeMapping:

  /** Convert a TypeRef to the language-specific type name. */
  def toLanguageType(ref: TypeRef): String

  /** Convert a TypeRef to a boxed type (for use in generics). Defaults to toLanguageType. */
  def toBoxedType(ref: TypeRef): String = toLanguageType(ref)

  /** Extract import statements needed for a TypeRef. */
  def importsFor(ref: TypeRef): Set[String] = Set.empty

  /** Fully qualified name → simple class name. */
  def simpleName(fqn: String): String =
    val lastDot = fqn.lastIndexOf('.')
    if lastDot >= 0 then fqn.substring(lastDot + 1) else fqn
