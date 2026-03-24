package io.alnovis.ircraft.dialect.scala3

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.scala3.emit.DirectScalaEmitter
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

class ScalaEmitterSuite extends munit.FunSuite:

  val emitter = DirectScalaEmitter()

  private def emitSingle(pkg: String, op: Operation): String =
    val file = FileOp(pkg, Vector(op))
    val module = Module("test", Vector(file))
    emitter.emit(module).values.head

  // ── Trait ──────────────────────────────────────────────────────────────

  test("emit trait"):
    val iface = InterfaceOp(
      name = "Money",
      methods = Vector(
        MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract)),
        MethodOp("getCurrency", TypeRef.STRING, modifiers = Set(Modifier.Public, Modifier.Abstract))
      )
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("package com.example"), s"Source:\n$source")
    assert(source.contains("trait Money"), s"Source:\n$source")
    assert(source.contains("def getAmount: Long"), s"Source:\n$source")
    assert(source.contains("def getCurrency: String"), s"Source:\n$source")

  test("emit trait with extends"):
    val iface = InterfaceOp(
      name = "Money",
      extendsTypes = List(TypeRef.NamedType("ProtoWrapper")),
      methods = Vector(MethodOp("getAmount", TypeRef.LONG, modifiers = Set(Modifier.Public, Modifier.Abstract)))
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("trait Money extends ProtoWrapper"), s"Source:\n$source")

  // ── Abstract class ─────────────────────────────────────────────────────

  test("emit abstract class with generics — square brackets"):
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
    assert(source.contains("abstract class AbstractMoney[PROTO <: Message]"), s"Source:\n$source")
    assert(source.contains("extends Money"), s"Source:\n$source")
    assert(source.contains("val proto: PROTO"), s"Source:\n$source")
    assert(source.contains("def extractAmount: Long"), s"Source:\n$source")

  // ── Concrete class ─────────────────────────────────────────────────────

  test("emit class with method body"):
    val cls = ClassOp(
      name = "MoneyV1",
      methods = Vector(
        MethodOp(
          "extractAmount",
          TypeRef.LONG,
          modifiers = Set(Modifier.Protected, Modifier.Override),
          body = Some(Block.of(
            Statement.ReturnStmt(Some(
              Expression.MethodCall(Some(Expression.Identifier("proto")), "getAmount")
            ))
          ))
        )
      )
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("override def extractAmount: Long"), s"Source:\n$source")
    assert(source.contains("proto.getAmount()"), s"Source:\n$source")

  // ── Enum ───────────────────────────────────────────────────────────────

  test("emit Scala 3 enum"):
    val enumOp = EnumClassOp(
      name = "Currency",
      constants = Vector(
        EnumConstantOp("USD", List(Expression.Literal("0", TypeRef.INT))),
        EnumConstantOp("EUR", List(Expression.Literal("1", TypeRef.INT)))
      ),
      fields = Vector(FieldDeclOp("value", TypeRef.INT, Set(Modifier.Private, Modifier.Final)))
    )
    val source = emitSingle("com.example", enumOp)
    assert(source.contains("enum Currency"), s"Source:\n$source")
    assert(source.contains("case USD extends Currency(0)"), s"Source:\n$source")
    assert(source.contains("case EUR extends Currency(1)"), s"Source:\n$source")

  // ── Type mapping ───────────────────────────────────────────────────────

  test("Array[Byte] type"):
    val iface = InterfaceOp(
      name = "Data",
      methods = Vector(MethodOp("getPayload", TypeRef.BYTES, modifiers = Set(Modifier.Public, Modifier.Abstract)))
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("def getPayload: Array[Byte]"), s"Source:\n$source")

  test("Option type for optional"):
    val iface = InterfaceOp(
      name = "Data",
      methods = Vector(MethodOp("getName", TypeRef.OptionalType(TypeRef.STRING), modifiers = Set(Modifier.Public, Modifier.Abstract)))
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("def getName: Option[String]"), s"Source:\n$source")

  test("List with square brackets"):
    val iface = InterfaceOp(
      name = "Data",
      methods = Vector(MethodOp("getItems", TypeRef.ListType(TypeRef.STRING), modifiers = Set(Modifier.Public, Modifier.Abstract)))
    )
    val source = emitSingle("com.example", iface)
    assert(source.contains("def getItems: List[String]"), s"Source:\n$source")

  // ── Expressions ────────────────────────────────────────────────────────

  test("cast uses asInstanceOf"):
    val cls = ClassOp(
      name = "Test",
      methods = Vector(MethodOp("convert", TypeRef.STRING,
        modifiers = Set(Modifier.Public),
        body = Some(Block.of(Statement.ReturnStmt(Some(
          Expression.Cast(Expression.Identifier("obj"), TypeRef.STRING)
        ))))))
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("obj.asInstanceOf[String]"), s"Source:\n$source")

  test("conditional uses if-then-else"):
    val cls = ClassOp(
      name = "Test",
      methods = Vector(MethodOp("check", TypeRef.STRING,
        modifiers = Set(Modifier.Public),
        body = Some(Block.of(Statement.ReturnStmt(Some(
          Expression.Conditional(
            Expression.Identifier("flag"),
            Expression.Literal("\"yes\"", TypeRef.STRING),
            Expression.Literal("\"no\"", TypeRef.STRING)
          )
        ))))))
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("if flag then"), s"Source:\n$source")

  // ── Companion object ───────────────────────────────────────────────────

  test("static fields become companion object"):
    val cls = ClassOp(
      name = "Versions",
      modifiers = Set(Modifier.Public, Modifier.Final),
      fields = Vector(
        FieldDeclOp("INSTANCE", TypeRef.NamedType("Versions"), Set(Modifier.Public, Modifier.Static, Modifier.Final),
          defaultValue = Some("Versions()"))
      )
    )
    val source = emitSingle("com.example", cls)
    assert(source.contains("object Versions"), s"Source:\n$source")
    assert(source.contains("val INSTANCE: Versions"), s"Source:\n$source")
