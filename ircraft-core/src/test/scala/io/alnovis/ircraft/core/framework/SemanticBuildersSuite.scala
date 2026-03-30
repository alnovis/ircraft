package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.ops.*
import io.alnovis.ircraft.core.semantic.expr.Expression

class SemanticBuildersSuite extends munit.FunSuite:

  test("interfaceFrom produces InterfaceOp with abstract getter methods"):
    given NameConverter = NameConverter.snakeCase
    val iface = SemanticBuilders.interfaceFrom(
      "Money",
      Vector("amount" -> TypeRef.LONG, "currency" -> TypeRef.STRING)
    )
    assertEquals(iface.name, "Money")
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(0).name, "getAmount")
    assertEquals(iface.methods(1).name, "getCurrency")
    assert(iface.methods(0).isAbstract)
    assert(iface.methods(1).isAbstract)

  test("interfaceFrom with extendsTypes"):
    given NameConverter = NameConverter.snakeCase
    val iface = SemanticBuilders.interfaceFrom(
      "ExtMoney",
      Vector("amount" -> TypeRef.LONG),
      extendsTypes = List(TypeRef.NamedType("Serializable"))
    )
    assertEquals(iface.extendsTypes, List(TypeRef.NamedType("Serializable")))

  test("classFrom produces ClassOp with fields, constructor, and getters"):
    given NameConverter = NameConverter.snakeCase
    val cls = SemanticBuilders.classFrom(
      "MoneyImpl",
      Vector(
        ("amount", TypeRef.LONG, true),
        ("currency", TypeRef.STRING, true)
      )
    )
    assertEquals(cls.name, "MoneyImpl")
    assertEquals(cls.fields.size, 2)
    assertEquals(cls.fields(0).name, "amount")
    assert(cls.fields(0).modifiers.contains(Modifier.Private))
    assert(cls.fields(0).modifiers.contains(Modifier.Final))
    assertEquals(cls.constructors.size, 1)
    assertEquals(cls.constructors(0).parameters.size, 2)
    assertEquals(cls.methods.size, 2)
    assertEquals(cls.methods(0).name, "getAmount")
    assertEquals(cls.methods(1).name, "getCurrency")

  test("classFrom with no required fields produces no constructor"):
    given NameConverter = NameConverter.snakeCase
    val cls = SemanticBuilders.classFrom(
      "Empty",
      Vector(("optional_field", TypeRef.STRING, false))
    )
    assert(cls.constructors.isEmpty)
    assertEquals(cls.fields.size, 1)
    assertEquals(cls.methods.size, 1)

  test("classFrom with superClass and implementsTypes"):
    given NameConverter = NameConverter.snakeCase
    val cls = SemanticBuilders.classFrom(
      "Sub",
      Vector(("id", TypeRef.INT, true)),
      superClass = Some(TypeRef.NamedType("Base")),
      implementsTypes = List(TypeRef.NamedType("Comparable"))
    )
    assertEquals(cls.superClass, Some(TypeRef.NamedType("Base")))
    assertEquals(cls.implementsTypes, List(TypeRef.NamedType("Comparable")))

  test("enumFrom with IntValued produces valued enum (Proto pattern)"):
    given NameConverter = NameConverter.snakeCase
    val e = SemanticBuilders.enumFrom(
      "Status",
      Vector(
        EnumValueMapper.IntValued("ok", 0),
        EnumValueMapper.IntValued("error", 1)
      )
    )
    assertEquals(e.name, "Status")
    assertEquals(e.constants.size, 2)
    assertEquals(e.constants(0).name, "OK")
    assertEquals(e.constants(1).name, "ERROR")
    assertEquals(e.constants(0).arguments.size, 1)
    assertEquals(e.constants(0).arguments.head, Expression.Literal("0", TypeRef.INT))
    assertEquals(e.fields.size, 1)
    assertEquals(e.fields(0).name, "value")
    assertEquals(e.constructors.size, 1)
    assertEquals(e.methods.size, 1)
    assertEquals(e.methods(0).name, "getValue")

  test("enumFrom with StringValued produces valued enum (OpenAPI pattern)"):
    given NameConverter = NameConverter.snakeCase
    val e = SemanticBuilders.enumFrom(
      "Color",
      Vector(
        EnumValueMapper.StringValued("red", "\"red\""),
        EnumValueMapper.StringValued("green", "\"green\"")
      )
    )
    assertEquals(e.name, "Color")
    assertEquals(e.constants.size, 2)
    assertEquals(e.constants(0).name, "RED")
    assertEquals(e.constants(1).name, "GREEN")
    assert(e.fields.nonEmpty)
    assert(e.constructors.nonEmpty)

  test("enumFrom with Simple produces simple enum (GraphQL pattern)"):
    given NameConverter = NameConverter.snakeCase
    val e = SemanticBuilders.enumFrom(
      "Direction",
      Vector(
        EnumValueMapper.Simple("north"),
        EnumValueMapper.Simple("south"),
        EnumValueMapper.Simple("east"),
        EnumValueMapper.Simple("west")
      )
    )
    assertEquals(e.name, "Direction")
    assertEquals(e.constants.size, 4)
    assertEquals(e.constants(0).name, "NORTH")
    assert(e.constants(0).arguments.isEmpty)
    assert(e.fields.isEmpty)
    assert(e.constructors.isEmpty)
    assert(e.methods.isEmpty)

  test("buildValuedEnum directly"):
    val constants = Vector(
      EnumConstantOp("A", arguments = List(Expression.Literal("1", TypeRef.INT))),
      EnumConstantOp("B", arguments = List(Expression.Literal("2", TypeRef.INT)))
    )
    val e = SemanticBuilders.buildValuedEnum("MyEnum", constants, TypeRef.INT)
    assertEquals(e.name, "MyEnum")
    assertEquals(e.constants.size, 2)
    assertEquals(e.fields.size, 1)
    assertEquals(e.constructors.size, 1)
    assertEquals(e.methods.size, 1)

  test("NameConverter affects generated names in interfaceFrom"):
    given NameConverter = NameConverter.mixed
    val iface = SemanticBuilders.interfaceFrom(
      "Config",
      Vector("max-retry-count" -> TypeRef.INT)
    )
    assertEquals(iface.methods(0).name, "getMaxRetryCount")

  test("NameConverter affects generated names in classFrom"):
    given NameConverter = NameConverter.mixed
    val cls = SemanticBuilders.classFrom(
      "Config",
      Vector(("max-retry-count", TypeRef.INT, true))
    )
    assertEquals(cls.fields(0).name, "maxRetryCount")
    assertEquals(cls.methods(0).name, "getMaxRetryCount")
    assertEquals(cls.constructors(0).parameters(0).name, "maxRetryCount")
