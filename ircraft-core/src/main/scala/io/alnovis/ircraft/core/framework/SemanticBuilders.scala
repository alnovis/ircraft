package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.ops.*
import io.alnovis.ircraft.core.semantic.expr.Expression

object SemanticBuilders:

  /** Build an InterfaceOp from generic parameters with abstract getter methods. */
  def interfaceFrom(
    name: String,
    fields: Vector[(String, TypeRef)],
    nestedTypes: Vector[Operation] = Vector.empty,
    extendsTypes: List[TypeRef] = Nil,
    attributes: AttributeMap = AttributeMap.empty
  )(using nc: NameConverter): InterfaceOp =
    val methods = fields.map: (fieldName, fieldType) =>
      MethodOp(
        name = nc.getterName(fieldName),
        returnType = fieldType,
        modifiers = Set(Modifier.Public, Modifier.Abstract)
      )
    InterfaceOp(
      name = name,
      methods = methods,
      nestedTypes = nestedTypes,
      extendsTypes = extendsTypes,
      attributes = attributes
    )

  /** Build a ClassOp from generic parameters with fields, getters, and optional constructor. */
  def classFrom(
    name: String,
    fields: Vector[(String, TypeRef, Boolean)],
    superClass: Option[TypeRef] = None,
    implementsTypes: List[TypeRef] = Nil,
    attributes: AttributeMap = AttributeMap.empty
  )(using nc: NameConverter): ClassOp =
    val fieldDecls = fields.map: (fieldName, fieldType, _) =>
      FieldDeclOp(
        name = nc.camelCase(fieldName),
        fieldType = fieldType,
        modifiers = Set(Modifier.Private, Modifier.Final)
      )
    val getterMethods = fields.map: (fieldName, fieldType, _) =>
      MethodOp(
        name = nc.getterName(fieldName),
        returnType = fieldType,
        modifiers = Set(Modifier.Public),
        body = Some(
          io.alnovis.ircraft.core.semantic.expr.Block.of(
            io.alnovis.ircraft.core.semantic.expr.Statement.ReturnStmt(
              Some(Expression.FieldAccess(Expression.ThisRef, nc.camelCase(fieldName)))
            )
          )
        )
      )
    val requiredFields = fields.filter(_._3)
    val constructors =
      if requiredFields.isEmpty then Vector.empty
      else
        Vector(
          ConstructorOp(
            parameters = requiredFields.map: (fieldName, fieldType, _) =>
              Parameter(nc.camelCase(fieldName), fieldType)
            .toList,
            modifiers = Set(Modifier.Public)
          )
        )
    ClassOp(
      name = name,
      superClass = superClass,
      implementsTypes = implementsTypes,
      fields = fieldDecls,
      constructors = constructors,
      methods = getterMethods,
      attributes = attributes
    )

  /** Build an EnumClassOp from generic values using EnumValueMapper. */
  def enumFrom[V](
    name: String,
    values: Vector[V],
    attributes: AttributeMap = AttributeMap.empty
  )(using mapper: EnumValueMapper[V], nc: NameConverter): EnumClassOp =
    val constants = values.map: v =>
      val args = v.constantArguments.map: (lit, litType) =>
        Expression.Literal(lit, litType)
      EnumConstantOp(
        name = nc.upperSnakeCase(v.constantName),
        arguments = args
      )
    val hasValues = constants.exists(_.arguments.nonEmpty)
    if hasValues then
      val valueType = values.head.constantArguments.head._2
      buildValuedEnum(name, constants, valueType, attributes)
    else
      EnumClassOp(
        name = name,
        constants = constants,
        attributes = attributes
      )

  /** Build a valued enum with a backing field, constructor, and getter. */
  def buildValuedEnum(
    name: String,
    constants: Vector[EnumConstantOp],
    valueType: TypeRef,
    attributes: AttributeMap = AttributeMap.empty
  ): EnumClassOp =
    val valueField = FieldDeclOp(
      name = "value",
      fieldType = valueType,
      modifiers = Set(Modifier.Private, Modifier.Final)
    )
    val ctor = ConstructorOp(
      parameters = List(Parameter("value", valueType)),
      modifiers = Set(Modifier.Private)
    )
    val getter = MethodOp(
      name = "getValue",
      returnType = valueType,
      modifiers = Set(Modifier.Public),
      body = Some(
        io.alnovis.ircraft.core.semantic.expr.Block.of(
          io.alnovis.ircraft.core.semantic.expr.Statement.ReturnStmt(
            Some(Expression.FieldAccess(Expression.ThisRef, "value"))
          )
        )
      )
    )
    EnumClassOp(
      name = name,
      constants = constants,
      fields = Vector(valueField),
      constructors = Vector(ctor),
      methods = Vector(getter),
      attributes = attributes
    )
