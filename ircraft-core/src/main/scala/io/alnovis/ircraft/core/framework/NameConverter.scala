package io.alnovis.ircraft.core.framework

trait NameConverter:
  def splitWords(name: String): IndexedSeq[String]

  def camelCase(name: String): String =
    val parts = splitWords(name)
    if parts.isEmpty then ""
    else parts.head.toLowerCase + parts.tail.map(capitalize).mkString

  def pascalCase(name: String): String =
    splitWords(name).map(capitalize).mkString

  def getterName(fieldName: String): String =
    s"get${pascalCase(fieldName)}"

  def setterName(fieldName: String): String =
    s"set${pascalCase(fieldName)}"

  def hasMethodName(fieldName: String): String =
    s"has${pascalCase(fieldName)}"

  def upperSnakeCase(name: String): String =
    splitWords(name).map(_.toUpperCase).mkString("_")

  private def capitalize(s: String): String =
    if s.isEmpty then s
    else s"${s.head.toUpper}${s.tail.toLowerCase}"

object NameConverter:
  val snakeCase: NameConverter = new NameConverter:
    def splitWords(name: String): IndexedSeq[String] =
      name.split("_").filter(_.nonEmpty).toIndexedSeq

  val mixed: NameConverter = new NameConverter:
    def splitWords(name: String): IndexedSeq[String] =
      name.split("[_\\-]").filter(_.nonEmpty).toIndexedSeq

  val identity: NameConverter = new NameConverter:
    def splitWords(name: String): IndexedSeq[String] = IndexedSeq(name)
    override def camelCase(name: String): String = name
    override def pascalCase(name: String): String = name
    override def getterName(fieldName: String): String = fieldName
    override def hasMethodName(fieldName: String): String = fieldName
    override def upperSnakeCase(name: String): String = name
