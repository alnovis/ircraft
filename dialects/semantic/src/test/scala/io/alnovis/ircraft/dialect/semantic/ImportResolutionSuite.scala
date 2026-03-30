package io.alnovis.ircraft.dialect.semantic

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.passes.ImportResolutionPass

class ImportResolutionSuite extends munit.FunSuite:

  private def module(files: FileOp*): IrModule =
    IrModule("test", files.toVector.map(_.asInstanceOf[Operation]).to(scala.collection.immutable.Vector))

  private def run(m: IrModule): (IrModule, List[DiagnosticMessage]) =
    val result = ImportResolutionPass.run(m, PassContext())
    (result.module, result.diagnostics)

  // ── Basic resolution ──────────────────────────────────────────────────

  test("resolves simple name to FQN across packages"):
    val apiFile = FileOp("com.example.api", Vector(
      InterfaceOp("Money")
    ))
    val implFile = FileOp("com.example.api.impl", Vector(
      ClassOp(
        name = "AbstractMoney",
        modifiers = Set(Modifier.Public, Modifier.Abstract),
        implementsTypes = List(TypeRef.NamedType("Money"))
      )
    ))

    val (resolved, diags) = run(module(apiFile, implFile))
    assert(diags.isEmpty)

    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.implementsTypes.head, TypeRef.NamedType("com.example.api.Money"))

  test("resolves superClass with ParameterizedType"):
    val apiFile = FileOp("com.example.api.impl", Vector(
      ClassOp("AbstractMoney", modifiers = Set(Modifier.Public, Modifier.Abstract))
    ))
    val implFile = FileOp("com.example.v1", Vector(
      ClassOp(
        name = "MoneyV1",
        superClass = Some(TypeRef.ParameterizedType(
          TypeRef.NamedType("AbstractMoney"),
          List(TypeRef.NamedType("PROTO"))
        ))
      )
    ))

    val (resolved, _) = run(module(apiFile, implFile))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp if c.name == "MoneyV1" => c }.get

    val superRef = cls.superClass.get.asInstanceOf[TypeRef.ParameterizedType]
    assertEquals(superRef.base, TypeRef.NamedType("com.example.api.impl.AbstractMoney"))
    assertEquals(superRef.typeArgs.head, TypeRef.NamedType("PROTO")) // type param unchanged

  // ── Nested types ──────────────────────────────────────────────────────

  test("resolves nested type to Parent.Nested FQN"):
    val apiFile = FileOp("com.example.api", Vector(
      InterfaceOp(
        "Address",
        nestedTypes = Vector(
          InterfaceOp("GeoLocation"),
          InterfaceOp("AddressType")
        )
      )
    ))
    val implFile = FileOp("com.example.api.impl", Vector(
      ClassOp(
        name = "AbstractAddress",
        methods = Vector(
          MethodOp("extractLocation", TypeRef.NamedType("GeoLocation")),
          MethodOp("extractType", TypeRef.NamedType("AddressType"))
        )
      )
    ))

    val (resolved, _) = run(module(apiFile, implFile))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.methods(0).returnType, TypeRef.NamedType("com.example.api.Address.GeoLocation"))
    assertEquals(cls.methods(1).returnType, TypeRef.NamedType("com.example.api.Address.AddressType"))

  test("resolves deeply nested types"):
    val file = FileOp("com.example", Vector(
      InterfaceOp(
        "Order",
        nestedTypes = Vector(
          InterfaceOp(
            "Item",
            nestedTypes = Vector(
              InterfaceOp("Variant")
            )
          )
        )
      )
    ))
    val refFile = FileOp("com.example.impl", Vector(
      ClassOp(
        name = "Foo",
        methods = Vector(MethodOp("getVariant", TypeRef.NamedType("Variant")))
      )
    ))

    val (resolved, _) = run(module(file, refFile))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.methods.head.returnType, TypeRef.NamedType("com.example.Order.Item.Variant"))

  // ── Safe fallback ─────────────────────────────────────────────────────

  test("leaves already-FQN names unchanged"):
    val file = FileOp("com.example", Vector(
      ClassOp(
        name = "Foo",
        superClass = Some(TypeRef.NamedType("com.google.protobuf.Message"))
      )
    ))

    val (resolved, _) = run(module(file))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.superClass.get, TypeRef.NamedType("com.google.protobuf.Message"))

  test("leaves type parameters unchanged"):
    val file = FileOp("com.example", Vector(
      ClassOp(
        name = "Foo",
        fields = Vector(FieldDeclOp("proto", TypeRef.NamedType("PROTO")))
      )
    ))

    val (resolved, _) = run(module(file))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.fields.head.fieldType, TypeRef.NamedType("PROTO"))

  // ── Collision detection ───────────────────────────────────────────────

  test("detects ambiguous names and emits warning"):
    val file1 = FileOp("com.example.api", Vector(InterfaceOp("Item")))
    val file2 = FileOp("com.example.other", Vector(InterfaceOp("Item")))
    val refFile = FileOp("com.example.impl", Vector(
      ClassOp(
        name = "Foo",
        implementsTypes = List(TypeRef.NamedType("Item"))
      )
    ))

    val (resolved, diags) = run(module(file1, file2, refFile))
    assert(diags.exists(_.message.contains("ambiguous")))

    // Ambiguous name left unchanged
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get
    assertEquals(cls.implementsTypes.head, TypeRef.NamedType("Item"))

  // ── Composite TypeRef resolution ──────────────────────────────────────

  test("resolves NamedType inside ListType"):
    val apiFile = FileOp("com.example.api", Vector(InterfaceOp("Money")))
    val file = FileOp("com.example.impl", Vector(
      ClassOp(
        name = "Foo",
        methods = Vector(
          MethodOp("getItems", TypeRef.ListType(TypeRef.NamedType("Money")))
        )
      )
    ))

    val (resolved, _) = run(module(apiFile, file))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.methods.head.returnType, TypeRef.ListType(TypeRef.NamedType("com.example.api.Money")))

  test("resolves NamedType inside MapType"):
    val apiFile = FileOp("com.example.api", Vector(InterfaceOp("Money")))
    val file = FileOp("com.example.impl", Vector(
      ClassOp(
        name = "Foo",
        fields = Vector(
          FieldDeclOp("cache", TypeRef.MapType(TypeRef.STRING, TypeRef.NamedType("Money")))
        )
      )
    ))

    val (resolved, _) = run(module(apiFile, file))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.fields.head.fieldType, TypeRef.MapType(TypeRef.STRING, TypeRef.NamedType("com.example.api.Money")))

  // ── Method parameters and constructors ────────────────────────────────

  test("resolves method parameter types"):
    val apiFile = FileOp("com.example.api", Vector(InterfaceOp("Money")))
    val file = FileOp("com.example.impl", Vector(
      ClassOp(
        name = "Foo",
        methods = Vector(
          MethodOp(
            "convert",
            TypeRef.VOID,
            parameters = List(Parameter("input", TypeRef.NamedType("Money")))
          )
        )
      )
    ))

    val (resolved, _) = run(module(apiFile, file))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp => c }.get

    assertEquals(cls.methods.head.parameters.head.paramType, TypeRef.NamedType("com.example.api.Money"))

  test("resolves constructor parameter types"):
    val apiFile = FileOp("com.example.api", Vector(ClassOp("ProtoMsg")))
    val file = FileOp("com.example.impl", Vector(
      ClassOp(
        name = "Foo",
        constructors = Vector(
          ConstructorOp(parameters = List(Parameter("proto", TypeRef.NamedType("ProtoMsg"))))
        )
      )
    ))

    val (resolved, _) = run(module(apiFile, file))
    val cls = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case c: ClassOp if c.name == "Foo" => c }.get

    assertEquals(cls.constructors.head.parameters.head.paramType, TypeRef.NamedType("com.example.api.ProtoMsg"))

  // ── Empty / no-op ─────────────────────────────────────────────────────

  test("empty module produces no errors"):
    val (resolved, diags) = run(module())
    assert(diags.isEmpty)
    assert(resolved.topLevel.isEmpty)

  test("resolves EnumClassOp implementsTypes"):
    val apiFile = FileOp("com.example.api", Vector(InterfaceOp("ProtoWrapper")))
    val file = FileOp("com.example.api", Vector(
      EnumClassOp(
        name = "Status",
        implementsTypes = List(TypeRef.NamedType("ProtoWrapper"))
      )
    ))

    val (resolved, _) = run(module(apiFile, file))
    val e = resolved.topLevel.collect { case f: FileOp => f.types }
      .flatten.collectFirst { case e: EnumClassOp => e }.get

    assertEquals(e.implementsTypes.head, TypeRef.NamedType("com.example.api.ProtoWrapper"))
