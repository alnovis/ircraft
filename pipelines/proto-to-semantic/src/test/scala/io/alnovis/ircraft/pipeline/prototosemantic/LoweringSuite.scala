package io.alnovis.ircraft.pipeline.prototosemantic

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.ops.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.semantic.ops.*

class LoweringSuite extends FunSuite:

  private def lower(ops: Operation*): IrModule =
    val module = IrModule("test", ops.toVector, AttributeMap.empty, None)
    ProtoToSemanticLowering.run(module, PassContext()).module

  // -- Name utils -------------------------------------------------------------

  test("snakeToCamelCase"):
    assertEquals(ProtoNameUtils.snakeToCamelCase("required_int32"), "requiredInt32")
    assertEquals(ProtoNameUtils.snakeToCamelCase("id"), "id")
    assertEquals(ProtoNameUtils.snakeToCamelCase("a_b_c"), "aBC")

  test("snakeToPascalCase"):
    assertEquals(ProtoNameUtils.snakeToPascalCase("payment_method"), "PaymentMethod")
    assertEquals(ProtoNameUtils.snakeToPascalCase("id"), "Id")

  test("getterName and hasMethodName"):
    assertEquals(ProtoNameUtils.getterName("amount"), "getAmount")
    assertEquals(ProtoNameUtils.hasMethodName("amount"), "hasAmount")
    assertEquals(ProtoNameUtils.getterName("required_int32"), "getRequiredInt32")

  test("caseEnumName and caseEnumGetterName"):
    assertEquals(ProtoNameUtils.caseEnumName("payment_method"), "PaymentMethodCase")
    assertEquals(ProtoNameUtils.caseEnumGetterName("payment_method"), "getPaymentMethodCase")

  // -- Simple message lowering ------------------------------------------------

  test("message with scalar fields -> interface with getters (proto3, no has)"):
    val file = ProtoFileOp(
      "com.example",
      ProtoSyntax.Proto3,
      messages = Vector(
        MessageOp("Money", fields = Vector(
          FieldOp("amount", 1, TypeRef.LONG),
          FieldOp("currency", 2, TypeRef.STRING)
        ))
      )
    )
    val result = lower(file)
    val fileOp = result.topLevel.head.asInstanceOf[FileOp]
    assertEquals(fileOp.packageName, "com.example")

    val iface = fileOp.types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.name, "Money")
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(0).name, "getAmount")
    assertEquals(iface.methods(0).returnType, TypeRef.LONG)
    assertEquals(iface.methods(1).name, "getCurrency")
    assert(iface.methods.forall(_.modifiers.contains(Modifier.Abstract)))

  test("proto.fieldNumber and proto.fieldName attributes on getters"):
    val file = ProtoFileOp(
      "pkg",
      messages = Vector(MessageOp("M", fields = Vector(FieldOp("my_field", 42, TypeRef.INT))))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val getter = iface.methods(0)
    assertEquals(getter.attributes.getInt("proto.fieldNumber"), Some(42))
    assertEquals(getter.attributes.getString("proto.fieldName"), Some("my_field"))

  // -- Has-method generation --------------------------------------------------

  test("proto3: OptionalType generates has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto3,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("nickname", 1, TypeRef.OptionalType(TypeRef.STRING))
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(0).name, "getNickname")
    assertEquals(iface.methods(0).returnType, TypeRef.STRING) // unwrapped
    assertEquals(iface.methods(1).name, "hasNickname")
    assertEquals(iface.methods(1).returnType, TypeRef.BOOL)

  test("proto3: NamedType (message ref) generates has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto3,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("address", 1, TypeRef.NamedType("com.example.Address"))
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(0).name, "getAddress")
    assertEquals(iface.methods(1).name, "hasAddress")

  test("proto3: plain scalar -- no has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto3,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("count", 1, TypeRef.INT)
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 1)

  test("proto3: ListType -- no has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto3,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("items", 1, TypeRef.ListType(TypeRef.STRING))
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 1)
    assertEquals(iface.methods(0).returnType, TypeRef.ListType(TypeRef.STRING))

  test("proto3: MapType -- no has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto3,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("tags", 1, TypeRef.MapType(TypeRef.STRING, TypeRef.INT))
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 1)

  test("proto2: OptionalType generates has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto2,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("name", 1, TypeRef.OptionalType(TypeRef.STRING))
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 2)

  test("proto2: plain scalar (required) -- no has-method"):
    val file = ProtoFileOp(
      "pkg",
      ProtoSyntax.Proto2,
      messages = Vector(MessageOp("M", fields = Vector(
        FieldOp("id", 1, TypeRef.INT)
      )))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods.size, 1)

  // -- Enum lowering ----------------------------------------------------------

  test("enum lowers to EnumClassOp with getValue"):
    val file = ProtoFileOp(
      "pkg",
      enums = Vector(EnumOp("Status", values = Vector(
        EnumValueOp("UNKNOWN", 0),
        EnumValueOp("ACTIVE", 1)
      )))
    )
    val result = lower(file)
    val enumOp = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[EnumClassOp]
    assertEquals(enumOp.name, "Status")
    assertEquals(enumOp.constants.size, 2)
    assertEquals(enumOp.constants(0).name, "UNKNOWN")
    assertEquals(enumOp.constants(1).name, "ACTIVE")
    assertEquals(enumOp.fields.size, 1)
    assertEquals(enumOp.fields(0).name, "value")
    assertEquals(enumOp.constructors.size, 1)
    assert(enumOp.constructors(0).modifiers.contains(Modifier.Private))
    assertEquals(enumOp.methods.size, 1)
    assertEquals(enumOp.methods(0).name, "getValue")

  // -- Oneof lowering ---------------------------------------------------------

  test("oneof produces case enum + discriminator + per-case getters"):
    val file = ProtoFileOp(
      "pkg",
      messages = Vector(MessageOp(
        "Payment",
        oneofs = Vector(OneofOp(
          "payment_method",
          fields = Vector(
            FieldOp("credit_card", 1, TypeRef.NamedType("CreditCard")),
            FieldOp("bank_account", 2, TypeRef.NamedType("BankAccount"))
          )
        ))
      ))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]

    // Case enum as nested type
    val caseEnum = iface.nestedTypes.collectFirst { case e: EnumClassOp => e }.get
    assertEquals(caseEnum.name, "PaymentMethodCase")
    assertEquals(caseEnum.constants.size, 3) // CREDIT_CARD, BANK_ACCOUNT, NOT_SET
    assertEquals(caseEnum.constants(0).name, "CREDIT_CARD")
    assertEquals(caseEnum.constants(1).name, "BANK_ACCOUNT")
    assertEquals(caseEnum.constants(2).name, "PAYMENT_METHOD_NOT_SET")

    // Discriminator getter
    assertEquals(iface.methods(0).name, "getPaymentMethodCase")
    assertEquals(iface.methods(0).returnType, TypeRef.NamedType("PaymentMethodCase"))

    // Per-case getters
    assertEquals(iface.methods(1).name, "getCreditCard")
    assertEquals(iface.methods(2).name, "getBankAccount")

  // -- Nested types -----------------------------------------------------------

  test("nested message -> nested InterfaceOp"):
    val file = ProtoFileOp(
      "pkg",
      messages = Vector(MessageOp(
        "Outer",
        fields = Vector(FieldOp("id", 1, TypeRef.INT)),
        nestedMessages = Vector(
          MessageOp("Inner", fields = Vector(FieldOp("value", 1, TypeRef.STRING)))
        )
      ))
    )
    val result = lower(file)
    val outer = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(outer.name, "Outer")
    val inner = outer.nestedTypes.collectFirst { case i: InterfaceOp => i }.get
    assertEquals(inner.name, "Inner")
    assertEquals(inner.methods(0).name, "getValue")

  test("nested enum -> nested EnumClassOp"):
    val file = ProtoFileOp(
      "pkg",
      messages = Vector(MessageOp(
        "Container",
        nestedEnums = Vector(EnumOp("Status", values = Vector(EnumValueOp("OK", 0))))
      ))
    )
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val nestedEnum = iface.nestedTypes.collectFirst { case e: EnumClassOp => e }.get
    assertEquals(nestedEnum.name, "Status")

  // -- File-level -------------------------------------------------------------

  test("file with message and enum -> two FileOps"):
    val file = ProtoFileOp(
      "com.example",
      messages = Vector(MessageOp("Money", fields = Vector(FieldOp("amount", 1, TypeRef.LONG)))),
      enums = Vector(EnumOp("Status", values = Vector(EnumValueOp("OK", 0))))
    )
    val result = lower(file)
    assertEquals(result.topLevel.size, 2)
    assert(result.topLevel.forall(_.isInstanceOf[FileOp]))

  test("FileOp carries proto.syntax attribute"):
    val file = ProtoFileOp("pkg", ProtoSyntax.Proto2, messages = Vector(MessageOp("M")))
    val result = lower(file)
    val fileOp = result.topLevel.head.asInstanceOf[FileOp]
    assertEquals(fileOp.attributes.getString("proto.syntax"), Some("Proto2"))

  test("source provenance attribute on lowered InterfaceOp"):
    val file = ProtoFileOp("pkg", messages = Vector(MessageOp("Money")))
    val result = lower(file)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.attributes.getString("ir.sourceNodeKind"), Some("proto.message"))

  // -- DSL integration --------------------------------------------------------

  test("full DSL -> lowering pipeline"):
    val file = ProtoSchema.file("com.example", ProtoSyntax.Proto3) { f =>
      f.message("Money") { msg =>
        msg.field("amount", 1, TypeRef.LONG)
        msg.field("currency", 2, TypeRef.STRING)
        msg.repeatedField("tags", 3, TypeRef.STRING)
        msg.mapField("metadata", 4, TypeRef.STRING, TypeRef.STRING)
      }
      f.enum_("Currency") { e =>
        e.value("UNKNOWN", 0)
        e.value("USD", 1)
        e.value("EUR", 2)
      }
    }
    val module = IrModule("test", Vector(file), AttributeMap.empty, None)
    val result = ProtoToSemanticLowering.run(module, PassContext())

    assert(result.isSuccess)
    assertEquals(result.module.topLevel.size, 2) // Money FileOp + Currency FileOp

    val moneyFile = result.module.topLevel(0).asInstanceOf[FileOp]
    val moneyIface = moneyFile.types.head.asInstanceOf[InterfaceOp]
    assertEquals(moneyIface.name, "Money")
    assertEquals(moneyIface.methods.size, 4) // getAmount, getCurrency, getTags, getMetadata

    val currencyFile = result.module.topLevel(1).asInstanceOf[FileOp]
    val currencyEnum = currencyFile.types.head.asInstanceOf[EnumClassOp]
    assertEquals(currencyEnum.name, "Currency")
    assertEquals(currencyEnum.constants.size, 3)

  test("non-proto operations pass through unchanged"):
    val nonProto = InterfaceOp(name = "Existing", methods = Vector(
      MethodOp("foo", TypeRef.VOID)
    ))
    val file = ProtoFileOp("pkg", messages = Vector(MessageOp("New")))
    val module = IrModule("test", Vector(nonProto, file), AttributeMap.empty, None)
    val result = ProtoToSemanticLowering.run(module, PassContext())

    // First op is the pass-through InterfaceOp, then the lowered FileOp
    assert(result.module.topLevel(0).isInstanceOf[InterfaceOp])
    assertEquals(result.module.topLevel(0).asInstanceOf[InterfaceOp].name, "Existing")
    assert(result.module.topLevel(1).isInstanceOf[FileOp])
