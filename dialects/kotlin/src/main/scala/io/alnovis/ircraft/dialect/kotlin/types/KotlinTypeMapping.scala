package io.alnovis.ircraft.dialect.kotlin.types

import io.alnovis.ircraft.core.BaseLanguageTypeMapping

/** Maps ircraft TypeRef to Kotlin type strings. */
object KotlinTypeMapping extends BaseLanguageTypeMapping:

  protected val bytesType: String = "ByteArray"
  protected val voidType: String  = "Unit"
  protected val unionType: String = "Any"

  protected def listType(elem: String): String                              = s"List<$elem>"
  protected def mapType(key: String, value: String): String                 = s"Map<$key, $value>"
  protected def optionalType(inner: String): String                         = s"$inner?"
  protected def parameterizedType(base: String, args: List[String]): String = s"$base<${args.mkString(", ")}>"
  protected def wildcardType(bound: Option[String]): String                 = bound.map(b => s"out $b").getOrElse("*")
