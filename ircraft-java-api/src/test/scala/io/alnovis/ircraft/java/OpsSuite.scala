package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.ops.*

class OpsSuite extends munit.FunSuite:

  test("iface builder creates InterfaceOp"):
    val m = Ops.method("getName", Types.STRING).abstractPublic().build()
    val iface = Ops
      .iface("ProtoWrapper")
      .javadoc("Base interface")
      .addMethod(m)
      .build()

    assertEquals(iface.name, "ProtoWrapper")
    assertEquals(iface.methods.size, 1)
    assertEquals(iface.methods(0).name, "getName")
    assertEquals(iface.javadoc, Some("Base interface"))

  test("cls builder creates ClassOp"):
    val cls = Ops
      .cls("AbstractMoney")
      .abstractClass()
      .superClass(Types.named("BaseWrapper"))
      .addTypeParam(TypeParam("PROTO"))
      .addMethod(Ops.method("extract", Types.LONG).protectedAbstract().build())
      .build()

    assertEquals(cls.name, "AbstractMoney")
    assert(cls.isAbstract)
    assertEquals(cls.superClass, Some(Types.named("BaseWrapper")))
    assertEquals(cls.methods.size, 1)

  test("file builder creates FileOp"):
    val iface = Ops.iface("Money").build()
    val file  = Ops.file("com.example.api").addType(iface).build()

    assertEquals(file.packageName, "com.example.api")
    assertEquals(file.types.size, 1)

  test("enumClass builder creates EnumClassOp"):
    val e = Ops
      .enumClass("Status")
      .constant("UNKNOWN", 0)
      .constant("ACTIVE", 1)
      .addMethod(Ops.method("getValue", Types.INT).build())
      .build()

    assertEquals(e.name, "Status")
    assertEquals(e.constants.size, 2)
    assertEquals(e.methods.size, 1)

  test("method builder with parameters"):
    val m = Ops
      .method("setAmount", Types.named("Builder"))
      .addParameter("amount", Types.LONG)
      .build()

    assertEquals(m.parameters.size, 1)
    assertEquals(m.parameters.head.name, "amount")

  test("method convenience modifiers"):
    val m1 = Ops.method("foo", Types.VOID).abstractPublic().build()
    assertEquals(m1.modifiers, Set(Modifier.Public, Modifier.Abstract))

    val m2 = Ops.method("bar", Types.VOID).protectedAbstract().build()
    assertEquals(m2.modifiers, Set(Modifier.Protected, Modifier.Abstract))

    val m3 = Ops.method("baz", Types.VOID).publicOverride().build()
    assertEquals(m3.modifiers, Set(Modifier.Public, Modifier.Override))

  test("rebuildIface preserves all fields and allows modification"):
    val original = InterfaceOp(
      name = "Money",
      extendsTypes = List(Types.named("Serializable")),
      methods = Vector(MethodOp("getName", TypeRef.STRING)),
      javadoc = Some("Original doc")
    )

    val rebuilt = Ops
      .rebuildIface(original)
      .addExtends(Types.named("ProtoWrapper"))
      .addMethod(Ops.method("toBytes", Types.BYTES).abstractPublic().build())
      .build()

    assertEquals(rebuilt.name, "Money")
    assertEquals(rebuilt.extendsTypes.size, 2)
    assertEquals(rebuilt.methods.size, 2)
    assertEquals(rebuilt.javadoc, Some("Original doc"))

  test("rebuildClass preserves all fields"):
    val original = ClassOp(
      name = "MoneyV1",
      modifiers = Set(Modifier.Public, Modifier.Abstract),
      superClass = Some(Types.named("AbstractMoney")),
      fields = Vector(FieldDeclOp("proto", Types.named("MoneyProto"))),
      methods = Vector(MethodOp("getName", TypeRef.STRING))
    )

    val rebuilt = Ops
      .rebuildClass(original)
      .addMethod(Ops.method("hashCode", Types.INT).publicOverride().build())
      .build()

    assertEquals(rebuilt.name, "MoneyV1")
    assert(rebuilt.isAbstract)
    assertEquals(rebuilt.superClass, Some(Types.named("AbstractMoney")))
    assertEquals(rebuilt.fields.size, 1)
    assertEquals(rebuilt.methods.size, 2)

  test("builder result matches smart constructor"):
    val fromBuilder = Ops
      .iface("Test")
      .addMethod(Ops.method("foo", Types.STRING).build())
      .build()

    val fromConstructor = InterfaceOp(
      name = "Test",
      methods = Vector(MethodOp("foo", TypeRef.STRING))
    )

    assertEquals(fromBuilder.contentHash, fromConstructor.contentHash)

  test("staticFinalField creates correct FieldDeclOp"):
    val f = Ops.staticFinalField("VERSION", Types.STRING, "v1")

    assertEquals(f.name, "VERSION")
    assertEquals(f.fieldType, Types.STRING)
    assert(f.modifiers.contains(Modifier.Public))
    assert(f.modifiers.contains(Modifier.Static))
    assert(f.modifiers.contains(Modifier.Final))
    assertEquals(f.defaultValue, Some("\"v1\""))

  test("privateConstructor creates correct ConstructorOp"):
    val c = Ops.privateConstructor()

    assertEquals(c.parameters, Nil)
    assert(c.modifiers.contains(Modifier.Private))

  test("file types accepts java.util.List"):
    val i1 = Ops.iface("A").build()
    val i2 = Ops.iface("B").build()
    val file = Ops
      .file("com.example")
      .types(java.util.List.of(i1, i2))
      .build()

    assertEquals(file.types.size, 2)

  test("staticFinalFieldRaw creates field without quoting"):
    val f = Ops.staticFinalFieldRaw("INSTANCE", Types.named("Foo"), "new Foo()")
    assertEquals(f.name, "INSTANCE")
    assertEquals(f.defaultValue, Some("new Foo()"))
    assert(f.modifiers.contains(Modifier.Static))
    assert(f.modifiers.contains(Modifier.Final))
