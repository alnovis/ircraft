package io.alnovis.ircraft.dialect.proto

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema

class ProtoDialectSuite extends FunSuite:

  test("build a simple message with fields"):
    val msg = MessageOp(
      "Money",
      fields = Vector(
        FieldOp("amount", 1, TypeRef.LONG),
        FieldOp("currency", 2, TypeRef.STRING)
      )
    )
    assertEquals(msg.name, "Money")
    assertEquals(msg.fields.size, 2)
    assertEquals(msg.fields(0).name, "amount")
    assertEquals(msg.fields(0).number, 1)
    assertEquals(msg.fields(1).fieldType, TypeRef.STRING)

  test("build enum with values"):
    val e = EnumOp(
      "Status",
      values = Vector(
        EnumValueOp("UNKNOWN", 0),
        EnumValueOp("ACTIVE", 1)
      )
    )
    assertEquals(e.name, "Status")
    assertEquals(e.values.size, 2)
    assertEquals(e.values(0).number, 0)

  test("build message with oneof"):
    val msg = MessageOp(
      "Payment",
      oneofs = Vector(
        OneofOp(
          "payment_method",
          fields = Vector(
            FieldOp("credit_card", 1, TypeRef.NamedType("CreditCard")),
            FieldOp("bank_account", 2, TypeRef.NamedType("BankAccount"))
          )
        )
      )
    )
    assertEquals(msg.oneofs.size, 1)
    assertEquals(msg.oneofs(0).name, "payment_method")
    assertEquals(msg.oneofs(0).fields.size, 2)

  test("build message with nested types"):
    val msg = MessageOp(
      "Outer",
      fields = Vector(FieldOp("id", 1, TypeRef.INT)),
      nestedMessages = Vector(
        MessageOp("Inner", fields = Vector(FieldOp("value", 1, TypeRef.STRING)))
      ),
      nestedEnums = Vector(
        EnumOp("InnerEnum", values = Vector(EnumValueOp("DEFAULT", 0)))
      )
    )
    assertEquals(msg.nestedMessages.size, 1)
    assertEquals(msg.nestedMessages(0).name, "Inner")
    assertEquals(msg.nestedEnums.size, 1)

  test("build proto file"):
    val file = ProtoFileOp(
      "com.example",
      ProtoSyntax.Proto3,
      options = Map("java_package" -> "com.example.proto"),
      messages = Vector(
        MessageOp("Money", fields = Vector(FieldOp("amount", 1, TypeRef.LONG)))
      ),
      enums = Vector(
        EnumOp("Currency", values = Vector(EnumValueOp("USD", 0)))
      )
    )
    assertEquals(file.protoPackage, "com.example")
    assertEquals(file.syntax, ProtoSyntax.Proto3)
    assertEquals(file.options("java_package"), "com.example.proto")
    assertEquals(file.messages.size, 1)
    assertEquals(file.enums.size, 1)

  test("field types: repeated, map, optional"):
    val msg = MessageOp(
      "Container",
      fields = Vector(
        FieldOp("tags", 1, TypeRef.ListType(TypeRef.STRING)),
        FieldOp("metadata", 2, TypeRef.MapType(TypeRef.STRING, TypeRef.INT)),
        FieldOp("nickname", 3, TypeRef.OptionalType(TypeRef.STRING))
      )
    )
    assert(msg.fields(0).fieldType.isInstanceOf[TypeRef.ListType])
    assert(msg.fields(1).fieldType.isInstanceOf[TypeRef.MapType])
    assert(msg.fields(2).fieldType.isInstanceOf[TypeRef.OptionalType])

  test("content hash is deterministic"):
    val f1 = FieldOp("amount", 1, TypeRef.LONG)
    val f2 = FieldOp("amount", 1, TypeRef.LONG)
    assertEquals(f1.contentHash, f2.contentHash)

  test("content hash differs for different fields"):
    val f1 = FieldOp("amount", 1, TypeRef.LONG)
    val f2 = FieldOp("amount", 2, TypeRef.LONG)
    assertNotEquals(f1.contentHash, f2.contentHash)

  test("content hash differs for different types"):
    val f1 = FieldOp("value", 1, TypeRef.INT)
    val f2 = FieldOp("value", 1, TypeRef.LONG)
    assertNotEquals(f1.contentHash, f2.contentHash)

  test("ProtoDialect owns proto operations"):
    val field = FieldOp("x", 1, TypeRef.INT)
    assert(ProtoDialect.owns(field))
    assertEquals(field.dialectName, "proto")
    assertEquals(field.opName, "field")

  test("DSL builds correct IR"):
    val file = ProtoSchema.file("com.example", ProtoSyntax.Proto3) { f =>
      f.message("Money") { msg =>
        msg.field("amount", 1, TypeRef.LONG)
        msg.field("currency", 2, TypeRef.STRING)
        msg.repeatedField("tags", 3, TypeRef.STRING)
        msg.mapField("metadata", 4, TypeRef.STRING, TypeRef.STRING)
      }
      f.enum_("Status") { e =>
        e.value("UNKNOWN", 0)
        e.value("ACTIVE", 1)
      }
    }
    assertEquals(file.protoPackage, "com.example")
    assertEquals(file.messages.size, 1)
    assertEquals(file.messages(0).fields.size, 4)
    assertEquals(file.messages(0).fields(2).fieldType, TypeRef.ListType(TypeRef.STRING))
    assertEquals(file.messages(0).fields(3).fieldType, TypeRef.MapType(TypeRef.STRING, TypeRef.STRING))
    assertEquals(file.enums.size, 1)

  test("DSL with oneof and nested types"):
    val file = ProtoSchema.file("com.example") { f =>
      f.message("Payment") { msg =>
        msg.field("id", 1, TypeRef.STRING)
        msg.oneof("method") { o =>
          o.field("card", 2, TypeRef.NamedType("Card"))
          o.field("cash", 3, TypeRef.BOOL)
        }
        msg.nestedMessage("Card") { nested =>
          nested.field("number", 1, TypeRef.STRING)
        }
        msg.nestedEnum("PaymentType") { e =>
          e.value("UNKNOWN", 0)
          e.value("ONLINE", 1)
        }
      }
    }
    val payment = file.messages(0)
    assertEquals(payment.oneofs.size, 1)
    assertEquals(payment.oneofs(0).fields.size, 2)
    assertEquals(payment.nestedMessages.size, 1)
    assertEquals(payment.nestedEnums.size, 1)

  test("DSL file options"):
    val file = ProtoSchema.file("com.example", ProtoSyntax.Proto2) { f =>
      f.option("java_package", "com.example.proto")
      f.option("java_outer_classname", "ExampleProto")
      f.message("Empty") { _ => }
    }
    assertEquals(file.syntax, ProtoSyntax.Proto2)
    assertEquals(file.options.size, 2)
    assertEquals(file.options("java_package"), "com.example.proto")

  test("estimatedSize counts all descendants"):
    val msg = MessageOp(
      "Big",
      fields = Vector(FieldOp("a", 1, TypeRef.INT), FieldOp("b", 2, TypeRef.INT)),
      nestedMessages = Vector(
        MessageOp("Nested", fields = Vector(FieldOp("x", 1, TypeRef.STRING)))
      )
    )
    // msg(1) + fields(2) + nestedMsg(1) + nestedField(1) = 5
    assertEquals(msg.estimatedSize, 5)

  test("mapChildren transforms fields"):
    val msg = MessageOp(
      "Test",
      fields = Vector(FieldOp("old", 1, TypeRef.INT))
    )
    val transformed = msg.mapChildren {
      case f: FieldOp => f.copy(name = "new")
      case other      => other
    }
    assertEquals(transformed.asInstanceOf[MessageOp].fields(0).name, "new")
