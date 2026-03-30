package io.alnovis.ircraft.dialect.proto.dsl

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*

import scala.collection.mutable

/** Builder DSL for constructing Proto IR. */
object ProtoSchema:

  def file(protoPackage: String, syntax: ProtoSyntax = ProtoSyntax.Proto3)(
    f: FileBuilder => Unit
  ): ProtoFileOp =
    val builder = FileBuilder(protoPackage, syntax)
    f(builder)
    builder.build()

class FileBuilder(protoPackage: String, syntax: ProtoSyntax):
  private val messages = mutable.ArrayBuffer[MessageOp]()
  private val enums = mutable.ArrayBuffer[EnumOp]()
  private val opts = mutable.LinkedHashMap[String, String]()

  def option(key: String, value: String): Unit = opts(key) = value

  def message(name: String)(f: MessageBuilder => Unit): Unit =
    val builder = MessageBuilder(name)
    f(builder)
    messages += builder.build()

  def enum_(name: String)(f: EnumBuilder => Unit): Unit =
    val builder = EnumBuilder(name)
    f(builder)
    enums += builder.build()

  def build(): ProtoFileOp = ProtoFileOp(
    protoPackage,
    syntax,
    opts.toMap,
    messages.toVector,
    enums.toVector
  )

class MessageBuilder(name: String):
  private val fields = mutable.ArrayBuffer[FieldOp]()
  private val oneofs = mutable.ArrayBuffer[OneofOp]()
  private val nestedMessages = mutable.ArrayBuffer[MessageOp]()
  private val nestedEnums = mutable.ArrayBuffer[EnumOp]()

  def field(name: String, number: Int, fieldType: TypeRef): Unit =
    fields += FieldOp(name, number, fieldType)

  def optionalField(name: String, number: Int, fieldType: TypeRef): Unit =
    fields += FieldOp(name, number, TypeRef.OptionalType(fieldType))

  def repeatedField(name: String, number: Int, fieldType: TypeRef): Unit =
    fields += FieldOp(name, number, TypeRef.ListType(fieldType))

  def mapField(name: String, number: Int, keyType: TypeRef, valueType: TypeRef): Unit =
    fields += FieldOp(name, number, TypeRef.MapType(keyType, valueType))

  def oneof(name: String)(f: OneofBuilder => Unit): Unit =
    val builder = OneofBuilder(name)
    f(builder)
    oneofs += builder.build()

  def nestedMessage(name: String)(f: MessageBuilder => Unit): Unit =
    val builder = MessageBuilder(name)
    f(builder)
    nestedMessages += builder.build()

  def nestedEnum(name: String)(f: EnumBuilder => Unit): Unit =
    val builder = EnumBuilder(name)
    f(builder)
    nestedEnums += builder.build()

  def build(): MessageOp = MessageOp(
    name,
    fields.toVector,
    oneofs.toVector,
    nestedMessages.toVector,
    nestedEnums.toVector
  )

class OneofBuilder(name: String):
  private val fields = mutable.ArrayBuffer[FieldOp]()

  def field(name: String, number: Int, fieldType: TypeRef): Unit =
    fields += FieldOp(name, number, fieldType)

  def build(): OneofOp = OneofOp(name, fields.toVector)

class EnumBuilder(name: String):
  private val values = mutable.ArrayBuffer[EnumValueOp]()

  def value(name: String, number: Int): Unit =
    values += EnumValueOp(name, number)

  def build(): EnumOp = EnumOp(name, values.toVector)
