package io.alnovis.ircraft.dialect.openapi.lowering

/** Name conversion utilities for OpenAPI to semantic IR lowering. */
object OpenApiNameUtils:

  /** `getUserById` -- already camelCase, or convert simple words. */
  def toCamelCase(name: String): String =
    if name.isEmpty then return ""
    if name.contains("_") || name.contains("-") then
      val parts = name.split("[_\\-]").filter(_.nonEmpty)
      if parts.isEmpty then ""
      else parts.head.toLowerCase + parts.tail.map(capitalize).mkString
    else
      // Preserve existing casing but ensure first char is lower
      name.head.toLower.toString + name.tail

  /** `user` -> `User`, `get_user` -> `GetUser` */
  def toPascalCase(name: String): String =
    if name.isEmpty then return ""
    if name.contains("_") || name.contains("-") then
      name.split("[_\\-]").filter(_.nonEmpty).map(capitalize).mkString
    else
      capitalize(name)

  /** `amount` -> `getAmount` */
  def getterName(fieldName: String): String =
    "get" + toPascalCase(fieldName)

  /** Derive API interface name from tags or path. */
  def apiInterfaceName(tags: List[String]): String =
    tags.headOption.map(toPascalCase).map(_ + "Api").getOrElse("Api")

  /** `ACTIVE` -> `ACTIVE`, `active` -> `ACTIVE` */
  def toEnumConstantName(name: String): String =
    if name.forall(c => c.isUpper || c == '_' || c.isDigit) then name
    else
      // Convert camelCase or PascalCase to UPPER_SNAKE_CASE
      val sb = new StringBuilder
      name.zipWithIndex.foreach { (c, i) =>
        if c.isUpper && i > 0 && name(i - 1).isLower then sb.append('_')
        sb.append(c.toUpper)
      }
      sb.toString

  private def capitalize(s: String): String =
    if s.isEmpty then s
    else s"${s.head.toUpper}${s.tail}"
