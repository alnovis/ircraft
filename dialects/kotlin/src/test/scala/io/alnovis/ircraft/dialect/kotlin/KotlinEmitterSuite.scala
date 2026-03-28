package io.alnovis.ircraft.dialect.kotlin

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.kotlin.emit.DirectKotlinEmitter
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

class KotlinEmitterSuite extends munit.FunSuite:

  val emitter = DirectKotlinEmitter()

  private def emitSingle(pkg: String, op: Operation): String =
    val file   = FileOp(pkg, Vector(op))
    val module = Module("test", Vector(file))
    emitter.emit(module).values.head

  // ── Interface ──────────────────────────────────────────────────────────

  test("emit interface"):
    val iface = InterfaceOp(
      name = "Money",
      methods = Vector(
        MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract)),
        MethodOp("getCurrency", TypeRef.STRING, modifiers = Set(Modifier.Public, Modifier.Abstract))
      )
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("package com.example"), s"Source:\n$source")
    assert(source.contains("interface Money"), s"Source:\n$source")
    assert(source.contains("fun getAmount(): Long"), s"Source:\n$source")
    assert(source.contains("fun getCurrency(): String"), s"Source:\n$source")
    assert(!source.contains(";"), "Kotlin should not have semicolons")

  test("emit interface with extends"):
    val iface = InterfaceOp(
      name = "Money",
      extendsTypes = List(TypeRef.NamedType("ProtoWrapper")),
      methods = Vector(MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract)))
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("interface Money : ProtoWrapper"), s"Source:\n$source")

  // ── Abstract class ─────────────────────────────────────────────────────

  test("emit abstract class with generics"):
    val cls = ClassOp(
      name = "AbstractMoney",
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      typeParams = List(TypeParam("PROTO", upperBounds = List(TypeRef.NamedType("com.google.protobuf.Message")))),
      implementsTypes = List(TypeRef.NamedType("Money")),
      fields = Vector(FieldDeclOp("proto", TypeRef.NamedType("PROTO"), Set(Modifier.Protected, Modifier.Final))),
      methods = Vector(
        MethodOp("extractAmount", TypeRef.LONG, modifiers = Set(Modifier.Protected, Modifier.Abstract))
      )
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("abstract class AbstractMoney<PROTO : Message>"), s"Source:\n$source")
    assert(source.contains("Money"), s"Should implement Money:\n$source")
    assert(source.contains("val proto: PROTO"), s"Source:\n$source")
    assert(source.contains("abstract fun extractAmount(): Long"), s"Source:\n$source")

  // ── Concrete class ─────────────────────────────────────────────────────

  test("emit concrete class with constructor and method body"):
    val cls = ClassOp(
      name = "MoneyV1",
      modifiers = Set(Modifier.Public),
      constructors = Vector(
        ConstructorOp(
          parameters = List(Parameter("proto", TypeRef.NamedType("MoneyProto"))),
          body = Some(
            Block.of(
              Statement.ExpressionStmt(
                Expression.MethodCall(Some(Expression.SuperRef), "this", List(Expression.Identifier("proto")))
              )
            )
          )
        )
      ),
      methods = Vector(
        MethodOp(
          "extractAmount",
          TypeRef.LONG,
          modifiers = Set(Modifier.Protected, Modifier.Override),
          body = Some(
            Block.of(
              Statement.ReturnStmt(
                Some(
                  Expression.MethodCall(Some(Expression.Identifier("proto")), "getAmount")
                )
              )
            )
          )
        )
      )
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("class MoneyV1"), s"Source:\n$source")
    assert(source.contains("constructor(proto: MoneyProto)"), s"Source:\n$source")
    assert(source.contains("override fun extractAmount(): Long"), s"Source:\n$source")
    assert(source.contains("return proto.getAmount()"), s"Source:\n$source")
    assert(!source.contains("new "), "Kotlin should not use 'new'")

  // ── Enum ───────────────────────────────────────────────────────────────

  test("emit enum class"):
    val enumOp = EnumClassOp(
      name = "Currency",
      constants = Vector(
        EnumConstantOp("USD", List(Expression.Literal("0", TypeRef.INT))),
        EnumConstantOp("EUR", List(Expression.Literal("1", TypeRef.INT)))
      ),
      fields = Vector(FieldDeclOp("value", TypeRef.INT, Set(Modifier.Private, Modifier.Final))),
      constructors = Vector(
        ConstructorOp(parameters = List(Parameter("value", TypeRef.INT)), modifiers = Set(Modifier.Private))
      ),
      methods = Vector(MethodOp("getValue", TypeRef.INT, modifiers = Set(Modifier.Public)))
    )
    val source = emitSingle("com.example", enumOp)
    assert(source.contains("enum class Currency"), s"Source:\n$source")
    assert(source.contains("USD(0)"), s"Source:\n$source")
    assert(source.contains("EUR(1)"), s"Source:\n$source")
    assert(source.contains("val value: Int"), s"Source:\n$source")

  // ── Type mapping ───────────────────────────────────────────────────────

  test("ByteArray type"):
    val iface = InterfaceOp(
      name = "Data",
      methods = Vector(MethodOp("getPayload", TypeRef.BYTES, modifiers = Set(Modifier.Public, Modifier.Abstract)))
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("fun getPayload(): ByteArray"), s"Source:\n$source")

  test("nullable optional type"):
    val iface = InterfaceOp(
      name = "Data",
      methods = Vector(
        MethodOp("getName", TypeRef.OptionalType(TypeRef.STRING), modifiers = Set(Modifier.Public, Modifier.Abstract))
      )
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("fun getName(): String?"), s"Source:\n$source")

  // ── Expressions ────────────────────────────────────────────────────────

  test("cast uses 'as' syntax"):
    val cls = ClassOp(
      name = "Test",
      methods = Vector(
        MethodOp(
          "convert",
          TypeRef.STRING,
          modifiers = Set(Modifier.Public),
          body = Some(
            Block.of(
              Statement.ReturnStmt(
                Some(
                  Expression.Cast(Expression.Identifier("obj"), TypeRef.STRING)
                )
              )
            )
          )
        )
      )
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("obj as String"), s"Source:\n$source")

  test("new instance without 'new' keyword"):
    val cls = ClassOp(
      name = "Test",
      methods = Vector(
        MethodOp(
          "create",
          TypeRef.NamedType("Money"),
          modifiers = Set(Modifier.Public),
          body = Some(
            Block.of(
              Statement.ReturnStmt(
                Some(
                  Expression.NewInstance(TypeRef.NamedType("Money"), List(Expression.Literal("100", TypeRef.LONG)))
                )
              )
            )
          )
        )
      )
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("Money(100)"), s"Source:\n$source")
    assert(!source.contains("new Money"), s"Source:\n$source")

  // ── Static -> companion object ──────────────────────────────────────────

  test("static fields become companion object"):
    val cls = ClassOp(
      name = "VersionContextV1",
      modifiers = Set(Modifier.Public, Modifier.Final),
      fields = Vector(
        FieldDeclOp(
          "INSTANCE",
          TypeRef.NamedType("VersionContextV1"),
          Set(Modifier.Public, Modifier.Static, Modifier.Final),
          defaultValue = Some("VersionContextV1()")
        )
      ),
      constructors = Vector(ConstructorOp(parameters = Nil, modifiers = Set(Modifier.Private)))
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("companion object"), s"Source:\n$source")
    assert(source.contains("val INSTANCE: VersionContextV1"), s"Source:\n$source")
