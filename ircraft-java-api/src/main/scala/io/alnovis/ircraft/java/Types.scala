package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.TypeRef

/** TypeRef constants and factories for Java consumers. */
object Types:

  // -- Primitives -----------------------------------------------------------

  val STRING: TypeRef = TypeRef.STRING
  val INT: TypeRef    = TypeRef.INT
  val LONG: TypeRef   = TypeRef.LONG
  val FLOAT: TypeRef  = TypeRef.FLOAT
  val DOUBLE: TypeRef = TypeRef.DOUBLE
  val BOOL: TypeRef   = TypeRef.BOOL
  val BYTES: TypeRef  = TypeRef.BYTES
  val VOID: TypeRef   = TypeRef.VOID

  // -- Factories ------------------------------------------------------------

  def named(fqn: String): TypeRef.NamedType = TypeRef.NamedType(fqn)

  def list(element: TypeRef): TypeRef.ListType = TypeRef.ListType(element)

  def map(key: TypeRef, value: TypeRef): TypeRef.MapType = TypeRef.MapType(key, value)

  def optional(inner: TypeRef): TypeRef.OptionalType = TypeRef.OptionalType(inner)
