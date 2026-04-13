package io.alnovis.ircraft.dialects.proto

import cats._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

class ProtoLoweringSuite extends munit.FunSuite {

  type F[A] = Id[A]

  private def lower(file: ProtoFile): Module[Fix[SemanticF]] =
    ProtoLowering.lower[F](file)

  /** Extract TypeDeclF from Fix[SemanticF] or fail. */
  private def asTypeDecl(fix: Fix[SemanticF]): TypeDeclF[Fix[SemanticF]] = fix match {
    case Decl.TypeDecl(td) => td
    case other => fail(s"expected TypeDeclF but got: $other")
  }

  /** Extract EnumDeclF from Fix[SemanticF] or fail. */
  private def asEnumDecl(fix: Fix[SemanticF]): EnumDeclF[Fix[SemanticF]] = fix match {
    case Decl.EnumDecl(ed) => ed
    case other => fail(s"expected EnumDeclF but got: $other")
  }

  private val simpleProto = ProtoFile(
    name = "user.proto",
    syntax = ProtoSyntax.Proto3,
    packageName = "com.example.proto",
    javaPackage = Some("com.example.proto"),
    javaOuterClassname = Some("UserOuterClass"),
    javaMultipleFiles = false,
    messages = Vector(
      ProtoMessage(
        name = "User",
        fields = Vector(
          ProtoField("id", 1, ProtoType.Int64, ProtoLabel.Required, None),
          ProtoField("name", 2, ProtoType.String, ProtoLabel.Required, None),
          ProtoField("email", 3, ProtoType.String, ProtoLabel.Optional, None),
          ProtoField("active", 4, ProtoType.Bool, ProtoLabel.Required, None)
        ),
        nestedMessages = Vector.empty,
        nestedEnums = Vector.empty,
        oneofs = Vector.empty
      )
    ),
    enums = Vector.empty
  )

  test("lower simple proto3 message to Protocol TypeDecl") {
    val module = lower(simpleProto)
    assertEquals(module.units.size, 1)
    val unit = module.units.head
    assertEquals(unit.namespace, "com.example.proto")

    val decl = asTypeDecl(unit.declarations.head)
    assertEquals(decl.name, "User")
    assertEquals(decl.kind, TypeKind.Protocol)
  }

  test("fields are lowered with correct types") {
    val module = lower(simpleProto)
    val decl   = asTypeDecl(module.units.head.declarations.head)

    val fieldTypes = decl.fields.map(f => f.name -> f.fieldType)
    assertEquals(
      fieldTypes,
      Vector(
        "id"     -> TypeExpr.LONG,
        "name"   -> TypeExpr.STR,
        "email"  -> TypeExpr.Optional(TypeExpr.STR),
        "active" -> TypeExpr.BOOL
      )
    )
  }

  test("getter functions are generated") {
    val module    = lower(simpleProto)
    val decl      = asTypeDecl(module.units.head.declarations.head)
    val funcNames = decl.functions.map(_.name)
    assert(funcNames.contains("getId"))
    assert(funcNames.contains("getName"))
    assert(funcNames.contains("getEmail"))
    assert(funcNames.contains("getActive"))
  }

  test("has-methods for proto3 optional fields") {
    val module     = lower(simpleProto)
    val decl       = asTypeDecl(module.units.head.declarations.head)
    val hasMethods = decl.functions.filter(_.name.startsWith("has"))
    // proto3: only optional fields get has-method
    assertEquals(hasMethods.size, 1)
    assertEquals(hasMethods.head.name, "hasEmail")
  }

  test("proto2 has-methods for all non-repeated") {
    val proto2 = simpleProto.copy(
      syntax = ProtoSyntax.Proto2,
      messages = Vector(
        ProtoMessage(
          name = "Msg",
          fields = Vector(
            ProtoField("x", 1, ProtoType.Int32, ProtoLabel.Required, None),
            ProtoField("y", 2, ProtoType.Int32, ProtoLabel.Optional, None),
            ProtoField("items", 3, ProtoType.Int32, ProtoLabel.Repeated, None)
          ),
          nestedMessages = Vector.empty,
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module     = lower(proto2)
    val decl       = asTypeDecl(module.units.head.declarations.head)
    val hasMethods = decl.functions.filter(_.name.startsWith("has")).map(_.name)
    // proto2: only optional gets has, required does not (always present), repeated does not
    assertEquals(hasMethods.toSet, Set("hasY"))
  }

  test("repeated field becomes ListOf") {
    val proto = simpleProto.copy(messages =
      Vector(
        ProtoMessage(
          name = "Container",
          fields = Vector(
            ProtoField("items", 1, ProtoType.String, ProtoLabel.Repeated, None)
          ),
          nestedMessages = Vector.empty,
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module = lower(proto)
    val decl   = asTypeDecl(module.units.head.declarations.head)
    assertEquals(decl.fields.head.fieldType, TypeExpr.ListOf(TypeExpr.STR))
  }

  test("map field becomes MapOf") {
    val proto = simpleProto.copy(messages =
      Vector(
        ProtoMessage(
          name = "Config",
          fields = Vector(
            ProtoField("entries", 1, ProtoType.Map(ProtoType.String, ProtoType.Int32), ProtoLabel.Repeated, None)
          ),
          nestedMessages = Vector.empty,
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module = lower(proto)
    val decl   = asTypeDecl(module.units.head.declarations.head)
    assertEquals(decl.fields.head.fieldType, TypeExpr.MapOf(TypeExpr.STR, TypeExpr.INT))
  }

  test("message reference becomes Unresolved") {
    val proto = simpleProto.copy(messages =
      Vector(
        ProtoMessage(
          name = "Order",
          fields = Vector(
            ProtoField(
              "address",
              1,
              ProtoType.Message("com.example.Address"),
              ProtoLabel.Required,
              Some("com.example.Address")
            )
          ),
          nestedMessages = Vector.empty,
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module = lower(proto)
    val decl   = asTypeDecl(module.units.head.declarations.head)
    assertEquals(decl.fields.head.fieldType, TypeExpr.Unresolved("com.example.Address"))
  }

  test("enum is lowered to EnumDecl") {
    val proto = simpleProto.copy(
      messages = Vector.empty,
      enums = Vector(
        ProtoEnum(
          "Status",
          Vector(
            ProtoEnumValue("UNKNOWN", 0),
            ProtoEnumValue("ACTIVE", 1),
            ProtoEnumValue("DELETED", 2)
          )
        )
      )
    )
    val module = lower(proto)
    val decl   = asEnumDecl(module.units.head.declarations.head)
    assertEquals(decl.name, "Status")
    assertEquals(decl.variants.size, 3)
    assertEquals(decl.variants.head.name, "UNKNOWN")
  }

  test("nested message becomes nested Decl") {
    val proto = simpleProto.copy(messages =
      Vector(
        ProtoMessage(
          name = "User",
          fields = Vector(ProtoField("name", 1, ProtoType.String, ProtoLabel.Required, None)),
          nestedMessages = Vector(
            ProtoMessage(
              name = "Address",
              fields = Vector(ProtoField("city", 1, ProtoType.String, ProtoLabel.Required, None)),
              nestedMessages = Vector.empty,
              nestedEnums = Vector.empty,
              oneofs = Vector.empty
            )
          ),
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module = lower(proto)
    val user   = asTypeDecl(module.units.head.declarations.head)
    assertEquals(user.nested.size, 1)
    val address = asTypeDecl(user.nested.head)
    assertEquals(address.name, "Address")
  }

  test("meta preserves proto provenance") {
    val module = lower(simpleProto)
    val decl   = asTypeDecl(module.units.head.declarations.head)
    assertEquals(decl.meta.get(ProtoMeta.sourceKind), Some("message"))
    assert(decl.meta.get(ProtoMeta.protoFqn).isDefined)
  }

  test("fieldKind meta on getter functions") {
    val proto = simpleProto.copy(messages =
      Vector(
        ProtoMessage(
          name = "Mixed",
          fields = Vector(
            ProtoField("id", 1, ProtoType.Int64, ProtoLabel.Required, None),
            ProtoField("address", 2, ProtoType.Message("Address"), ProtoLabel.Required, Some("Address")),
            ProtoField("items", 3, ProtoType.String, ProtoLabel.Repeated, None)
          ),
          nestedMessages = Vector.empty,
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module = lower(proto)
    val decl   = asTypeDecl(module.units.head.declarations.head)
    val kinds  = decl.functions.filter(_.name.startsWith("get")).map(f => f.name -> f.meta.get(ProtoMeta.fieldKind))
    assertEquals(
      kinds,
      Vector(
        "getId"      -> Some("SCALAR"),
        "getAddress" -> Some("MESSAGE"),
        "getItems"   -> Some("REPEATED_SCALAR")
      )
    )
  }

  test("lowerAll merges multiple files") {
    val file1 = simpleProto
    val file2 = simpleProto.copy(
      name = "order.proto",
      messages = Vector(
        ProtoMessage(
          name = "Order",
          fields = Vector(ProtoField("total", 1, ProtoType.Double, ProtoLabel.Required, None)),
          nestedMessages = Vector.empty,
          nestedEnums = Vector.empty,
          oneofs = Vector.empty
        )
      )
    )
    val module = ProtoLowering.lowerAll[F](Vector(file1, file2))
    assertEquals(module.units.size, 2)
  }
}
