package io.alnovis.ircraft.dialect.graphql.dsl

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.ops.*

import scala.collection.mutable

/** Builder DSL for constructing GraphQL IR. */
object GraphQlSchema:

  def schema(queryType: String = "Query")(
    f: SchemaBuilder => Unit
  ): GqlSchemaOp =
    val builder = SchemaBuilder(queryType)
    f(builder)
    builder.build()

class SchemaBuilder(queryType: String):
  private var mutType: Option[String] = None
  private var subType: Option[String] = None
  private val types = mutable.ArrayBuffer[Operation]()
  private val directives = mutable.ArrayBuffer[DirectiveDefOp]()

  def mutationType(name: String): Unit =
    mutType = Some(name)

  def subscriptionType(name: String): Unit =
    subType = Some(name)

  def objectType(name: String, implements: List[String] = Nil)(f: ObjectTypeBuilder => Unit): Unit =
    val builder = ObjectTypeBuilder(name, implements)
    f(builder)
    types += builder.build()

  def inputType(name: String)(f: InputTypeBuilder => Unit): Unit =
    val builder = InputTypeBuilder(name)
    f(builder)
    types += builder.build()

  def interfaceType(name: String, implements: List[String] = Nil)(f: InterfaceTypeBuilder => Unit): Unit =
    val builder = InterfaceTypeBuilder(name, implements)
    f(builder)
    types += builder.build()

  def unionType(name: String, members: List[String]): Unit =
    types += UnionTypeOp(name, members)

  def enumType(name: String)(f: EnumTypeBuilder => Unit): Unit =
    val builder = EnumTypeBuilder(name)
    f(builder)
    types += builder.build()

  def scalar(name: String, specifiedBy: Option[String] = None): Unit =
    types += ScalarTypeOp(name, specifiedBy)

  def directive(name: String, locations: List[DirectiveLocation] = Nil, repeatable: Boolean = false)(
    f: DirectiveBuilder => Unit = _ => ()
  ): Unit =
    val builder = DirectiveBuilder(name, locations, repeatable)
    f(builder)
    directives += builder.build()

  def build(): GqlSchemaOp = GqlSchemaOp(
    queryType,
    mutType,
    subType,
    types = types.toVector,
    directives = directives.toVector
  )

class ObjectTypeBuilder(name: String, implements: List[String]):
  private val fields = mutable.ArrayBuffer[GqlFieldOp]()

  def field(
    name: String,
    fieldType: TypeRef,
    description: Option[String] = None
  )(f: FieldBuilder => Unit = _ => ()): Unit =
    val builder = FieldBuilder(name, fieldType, description)
    f(builder)
    fields += builder.build()

  def deprecatedField(name: String, fieldType: TypeRef, reason: String): Unit =
    fields += GqlFieldOp(name, fieldType, description = None, isDeprecated = true, deprecationReason = Some(reason))

  def build(): ObjectTypeOp = ObjectTypeOp(name, implements, fields = fields.toVector)

class FieldBuilder(name: String, fieldType: TypeRef, description: Option[String]):
  private val arguments = mutable.ArrayBuffer[GqlArgumentOp]()

  def argument(name: String, argType: TypeRef, defaultValue: Option[String] = None): Unit =
    arguments += GqlArgumentOp(name, argType, defaultValue)

  def build(): GqlFieldOp = GqlFieldOp(name, fieldType, description, arguments = arguments.toVector)

class InputTypeBuilder(name: String):
  private val fields = mutable.ArrayBuffer[InputFieldOp]()

  def field(name: String, fieldType: TypeRef, defaultValue: Option[String] = None): Unit =
    fields += InputFieldOp(name, fieldType, defaultValue)

  def build(): InputObjectTypeOp = InputObjectTypeOp(name, fields = fields.toVector)

class InterfaceTypeBuilder(name: String, implements: List[String]):
  private val fields = mutable.ArrayBuffer[GqlFieldOp]()

  def field(
    name: String,
    fieldType: TypeRef,
    description: Option[String] = None
  )(f: FieldBuilder => Unit = _ => ()): Unit =
    val builder = FieldBuilder(name, fieldType, description)
    f(builder)
    fields += builder.build()

  def deprecatedField(name: String, fieldType: TypeRef, reason: String): Unit =
    fields += GqlFieldOp(name, fieldType, description = None, isDeprecated = true, deprecationReason = Some(reason))

  def build(): InterfaceTypeOp = InterfaceTypeOp(name, implements, fields = fields.toVector)

class EnumTypeBuilder(name: String):
  private val values = mutable.ArrayBuffer[GqlEnumValueOp]()

  def value(name: String): Unit =
    values += GqlEnumValueOp(name)

  def deprecatedValue(name: String, reason: String): Unit =
    values += GqlEnumValueOp(name, isDeprecated = true, deprecationReason = Some(reason))

  def build(): EnumTypeOp = EnumTypeOp(name, values = values.toVector)

class DirectiveBuilder(name: String, locations: List[DirectiveLocation], repeatable: Boolean):
  private val arguments = mutable.ArrayBuffer[GqlArgumentOp]()

  def argument(name: String, argType: TypeRef, defaultValue: Option[String] = None): Unit =
    arguments += GqlArgumentOp(name, argType, defaultValue)

  def build(): DirectiveDefOp = DirectiveDefOp(name, locations, repeatable, arguments = arguments.toVector)
