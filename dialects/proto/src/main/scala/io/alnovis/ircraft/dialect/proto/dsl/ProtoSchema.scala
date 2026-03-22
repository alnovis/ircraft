package io.alnovis.ircraft.dialect.proto.dsl

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.dialect.proto.types.*

/** DSL for building Proto Dialect IR.
  *
  * Usage:
  * {{{
  * val schema = ProtoSchema.build("v1", "v2") { schema =>
  *   schema.message("Money", "v1", "v2") { msg =>
  *     msg.field("amount", 1, TypeRef.LONG)
  *     msg.field("currency", 2, TypeRef.STRING)
  *   }
  *   schema.enum_("Status", "v1", "v2") { e =>
  *     e.value("UNKNOWN", 0)
  *     e.value("ACTIVE", 1)
  *   }
  * }
  * }}}
  */
object ProtoSchema:

  def build(versions: String*)(f: SchemaBuilder => Unit): SchemaOp =
    val builder = SchemaBuilder(versions.toList)
    f(builder)
    builder.build()

class SchemaBuilder(versions: List[String]):
  private var messages = Vector.empty[MessageOp]
  private var enums = Vector.empty[EnumOp]
  private var conflictEnums = Vector.empty[ConflictEnumOp]
  private var syntaxMap = Map.empty[String, ProtoSyntax]

  def syntax(version: String, s: ProtoSyntax): Unit =
    syntaxMap = syntaxMap + (version -> s)

  def message(name: String, presentIn: String*)(f: MessageBuilder => Unit): Unit =
    val vers = if presentIn.isEmpty then versions.toSet else presentIn.toSet
    val builder = MessageBuilder(name, vers)
    f(builder)
    messages = messages :+ builder.build()

  def enum_(name: String, presentIn: String*)(f: EnumBuilder => Unit): Unit =
    val vers = if presentIn.isEmpty then versions.toSet else presentIn.toSet
    val builder = EnumBuilder(name, vers)
    f(builder)
    enums = enums :+ builder.build()

  def conflictEnum(fieldName: String, enumName: String, messageName: String)(f: EnumBuilder => Unit): Unit =
    val builder = EnumBuilder(enumName, Set.empty)
    f(builder)
    conflictEnums = conflictEnums :+ ConflictEnumOp(fieldName, enumName, messageName, builder.buildValues())

  def build(): SchemaOp = SchemaOp(versions, syntaxMap, messages, enums, conflictEnums)

class MessageBuilder(name: String, presentInVersions: Set[String]):
  private var fields = Vector.empty[FieldOp]
  private var oneofs = Vector.empty[OneofOp]
  private var nestedMessages = Vector.empty[MessageOp]
  private var nestedEnums = Vector.empty[EnumOp]

  def field(
      name: String,
      number: Int,
      fieldType: TypeRef,
      conflictType: ConflictType = ConflictType.None,
      presentIn: Set[String] = Set.empty,
      optional: Boolean = false,
      repeated: Boolean = false,
      map: Boolean = false,
  ): Unit =
    val vers = if presentIn.isEmpty then presentInVersions else presentIn
    val javaName = snakeToCamel(name)
    fields = fields :+ FieldOp(name, javaName, number, fieldType, conflictType, vers, optional, repeated, map)

  def oneof(protoName: String, presentIn: String*)(f: OneofBuilder => Unit): Unit =
    val vers = if presentIn.isEmpty then presentInVersions else presentIn.toSet
    val builder = OneofBuilder(protoName, vers)
    f(builder)
    oneofs = oneofs :+ builder.build()

  def nestedMessage(name: String, presentIn: String*)(f: MessageBuilder => Unit): Unit =
    val vers = if presentIn.isEmpty then presentInVersions else presentIn.toSet
    val builder = MessageBuilder(name, vers)
    f(builder)
    nestedMessages = nestedMessages :+ builder.build()

  def nestedEnum(name: String, presentIn: String*)(f: EnumBuilder => Unit): Unit =
    val vers = if presentIn.isEmpty then presentInVersions else presentIn.toSet
    val builder = EnumBuilder(name, vers)
    f(builder)
    nestedEnums = nestedEnums :+ builder.build()

  def build(): MessageOp = MessageOp(name, presentInVersions, fields, oneofs, nestedMessages, nestedEnums)

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.isEmpty then s
    else parts.head + parts.tail.map(_.capitalize).mkString

class OneofBuilder(protoName: String, presentInVersions: Set[String]):
  private var fields = Vector.empty[FieldOp]

  def field(name: String, number: Int, fieldType: TypeRef, presentIn: Set[String] = Set.empty): Unit =
    val vers = if presentIn.isEmpty then presentInVersions else presentIn
    val javaName = snakeToCamel(name)
    fields = fields :+ FieldOp(name, javaName, number, fieldType, presentInVersions = vers)

  def build(): OneofOp =
    val javaName = snakeToCamel(protoName)
    val caseEnumName = protoName.split("_").map(_.capitalize).mkString + "Case"
    OneofOp(protoName, javaName, caseEnumName, presentInVersions, fields)

  private def snakeToCamel(s: String): String =
    val parts = s.split("_")
    if parts.isEmpty then s
    else parts.head + parts.tail.map(_.capitalize).mkString

class EnumBuilder(name: String, presentInVersions: Set[String]):
  private var values = Vector.empty[EnumValueOp]

  def value(name: String, number: Int, presentIn: Set[String] = Set.empty): Unit =
    val vers = if presentIn.isEmpty then presentInVersions else presentIn
    values = values :+ EnumValueOp(name, number, vers)

  def build(): EnumOp = EnumOp(name, presentInVersions, values)

  def buildValues(): Vector[EnumValueOp] = values
