package io.alnovis.ircraft.dialect.proto.lowering

/** snake_case to camelCase/PascalCase name conversion utilities for protobuf. */
object ProtoNameUtils:

  /** `required_int32` -> `requiredInt32` */
  def snakeToCamelCase(snake: String): String =
    val parts = snake.split("_").filter(_.nonEmpty)
    if parts.isEmpty then ""
    else parts.head.toLowerCase + parts.tail.map(capitalize).mkString

  /** `payment_method` -> `PaymentMethod` */
  def snakeToPascalCase(snake: String): String =
    snake.split("_").filter(_.nonEmpty).map(capitalize).mkString

  /** `amount` -> `getAmount` */
  def getterName(fieldName: String): String =
    "get" + snakeToPascalCase(fieldName)

  /** `amount` -> `hasAmount` */
  def hasMethodName(fieldName: String): String =
    "has" + snakeToPascalCase(fieldName)

  /** `payment_method` -> `PaymentMethodCase` */
  def caseEnumName(oneofName: String): String =
    snakeToPascalCase(oneofName) + "Case"

  /** `payment_method` -> `getPaymentMethodCase` */
  def caseEnumGetterName(oneofName: String): String =
    "get" + snakeToPascalCase(oneofName) + "Case"

  private def capitalize(s: String): String =
    if s.isEmpty then s
    else s"${s.head.toUpper}${s.tail.toLowerCase}"
