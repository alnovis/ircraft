package io.alnovis.ircraft.dialect.scala3.types

import io.alnovis.ircraft.core.BaseLanguageTypeMapping

/** Maps ircraft TypeRef to Scala 3 type strings. */
object ScalaTypeMapping extends BaseLanguageTypeMapping:

  protected val bytesType: String = "Array[Byte]"
  protected val voidType: String  = "Unit"
  protected val unionType: String = "Any"

  protected def listType(elem: String): String                              = s"List[$elem]"
  protected def mapType(key: String, value: String): String                 = s"Map[$key, $value]"
  protected def optionalType(inner: String): String                         = s"Option[$inner]"
  protected def parameterizedType(base: String, args: List[String]): String = s"$base[${args.mkString(", ")}]"
  protected def wildcardType(bound: Option[String]): String                 = bound.map(b => s"_ <: $b").getOrElse("_")
