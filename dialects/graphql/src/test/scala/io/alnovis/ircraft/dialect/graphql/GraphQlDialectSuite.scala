package io.alnovis.ircraft.dialect.graphql

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.ops.*
import io.alnovis.ircraft.dialect.graphql.dsl.GraphQlSchema

class GraphQlDialectSuite extends FunSuite:

  test("build object type with fields and arguments"):
    val obj = ObjectTypeOp(
      "User",
      fields = Vector(
        GqlFieldOp("id", TypeRef.NamedType("ID")),
        GqlFieldOp("name", TypeRef.STRING),
        GqlFieldOp(
          "posts",
          TypeRef.ListType(TypeRef.NamedType("Post")),
          arguments = Vector(
            GqlArgumentOp("first", TypeRef.OptionalType(TypeRef.INT)),
            GqlArgumentOp("after", TypeRef.OptionalType(TypeRef.STRING))
          )
        )
      )
    )
    assertEquals(obj.name, "User")
    assertEquals(obj.fields.size, 3)
    assertEquals(obj.fields(0).name, "id")
    assertEquals(obj.fields(0).fieldType, TypeRef.NamedType("ID"))
    assertEquals(obj.fields(2).arguments.size, 2)
    assertEquals(obj.fields(2).arguments(0).name, "first")

  test("build object type implementing interfaces"):
    val obj = ObjectTypeOp(
      "Cat",
      implementsInterfaces = List("Animal", "Pet"),
      fields = Vector(GqlFieldOp("name", TypeRef.STRING))
    )
    assertEquals(obj.implementsInterfaces, List("Animal", "Pet"))

  test("build input object type"):
    val inp = InputObjectTypeOp(
      "CreateUserInput",
      fields = Vector(
        InputFieldOp("name", TypeRef.STRING),
        InputFieldOp("email", TypeRef.STRING),
        InputFieldOp("age", TypeRef.OptionalType(TypeRef.INT), defaultValue = Some("18"))
      )
    )
    assertEquals(inp.name, "CreateUserInput")
    assertEquals(inp.fields.size, 3)
    assertEquals(inp.fields(2).defaultValue, Some("18"))

  test("build enum type"):
    val enm = EnumTypeOp(
      "Status",
      values = Vector(
        GqlEnumValueOp("ACTIVE"),
        GqlEnumValueOp("INACTIVE"),
        GqlEnumValueOp("DEPRECATED_VALUE", isDeprecated = true, deprecationReason = Some("Use INACTIVE"))
      )
    )
    assertEquals(enm.name, "Status")
    assertEquals(enm.values.size, 3)
    assertEquals(enm.values(2).isDeprecated, true)
    assertEquals(enm.values(2).deprecationReason, Some("Use INACTIVE"))

  test("build union type"):
    val uni = UnionTypeOp("SearchResult", List("User", "Post", "Comment"))
    assertEquals(uni.name, "SearchResult")
    assertEquals(uni.memberTypes, List("User", "Post", "Comment"))

  test("build interface type"):
    val ifc = InterfaceTypeOp(
      "Node",
      fields = Vector(GqlFieldOp("id", TypeRef.NamedType("ID")))
    )
    assertEquals(ifc.name, "Node")
    assertEquals(ifc.fields.size, 1)

  test("build scalar type"):
    val scl = ScalarTypeOp("DateTime", specifiedByUrl = Some("https://scalars.graphql.org/andimarek/date-time"))
    assertEquals(scl.name, "DateTime")
    assertEquals(scl.specifiedByUrl, Some("https://scalars.graphql.org/andimarek/date-time"))

  test("build directive definition with arguments"):
    val dir = DirectiveDefOp(
      "deprecated",
      locations = List(DirectiveLocation.FieldDefinition, DirectiveLocation.EnumValue),
      repeatable = false,
      arguments = Vector(
        GqlArgumentOp("reason", TypeRef.OptionalType(TypeRef.STRING), defaultValue = Some("No longer supported"))
      )
    )
    assertEquals(dir.name, "deprecated")
    assertEquals(dir.locations.size, 2)
    assertEquals(dir.repeatable, false)
    assertEquals(dir.arguments.size, 1)
    assertEquals(dir.arguments(0).name, "reason")

  test("build full schema"):
    val schema = GqlSchemaOp(
      "Query",
      mutationType = Some("Mutation"),
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(
          GqlFieldOp("user", TypeRef.OptionalType(TypeRef.NamedType("User")),
            arguments = Vector(GqlArgumentOp("id", TypeRef.NamedType("ID"))))
        )),
        ObjectTypeOp("User", fields = Vector(
          GqlFieldOp("id", TypeRef.NamedType("ID")),
          GqlFieldOp("name", TypeRef.STRING)
        ))
      )
    )
    assertEquals(schema.queryType, "Query")
    assertEquals(schema.mutationType, Some("Mutation"))
    assertEquals(schema.subscriptionType, None)
    assertEquals(schema.types.size, 2)

  test("content hash is deterministic"):
    val f1 = GqlFieldOp("name", TypeRef.STRING)
    val f2 = GqlFieldOp("name", TypeRef.STRING)
    assertEquals(f1.contentHash, f2.contentHash)

  test("content hash differs for different fields"):
    val f1 = GqlFieldOp("name", TypeRef.STRING)
    val f2 = GqlFieldOp("email", TypeRef.STRING)
    assertNotEquals(f1.contentHash, f2.contentHash)

  test("content hash differs for different types"):
    val f1 = GqlFieldOp("value", TypeRef.INT)
    val f2 = GqlFieldOp("value", TypeRef.STRING)
    assertNotEquals(f1.contentHash, f2.contentHash)

  test("GraphQlDialect owns graphql operations"):
    val field = GqlFieldOp("x", TypeRef.INT)
    assert(GraphQlDialect.owns(field))
    assertEquals(field.dialectName, "graphql")
    assertEquals(field.opName, "gql_field")

  test("DSL builds correct structure"):
    val schema = GraphQlSchema.schema("Query") { s =>
      s.mutationType("Mutation")
      s.objectType("Query") { obj =>
        obj.field("users", TypeRef.ListType(TypeRef.NamedType("User"))) { f =>
          f.argument("limit", TypeRef.OptionalType(TypeRef.INT))
        }
      }
      s.objectType("User", implements = List("Node")) { obj =>
        obj.field("id", TypeRef.NamedType("ID"))()
        obj.field("name", TypeRef.STRING)()
        obj.deprecatedField("oldName", TypeRef.STRING, "Use name instead")
      }
      s.inputType("CreateUserInput") { inp =>
        inp.field("name", TypeRef.STRING)
        inp.field("email", TypeRef.STRING)
      }
      s.interfaceType("Node") { ifc =>
        ifc.field("id", TypeRef.NamedType("ID"))()
      }
      s.unionType("SearchResult", List("User", "Post"))
      s.enumType("Role") { e =>
        e.value("ADMIN")
        e.value("USER")
        e.deprecatedValue("GUEST", "No longer supported")
      }
      s.scalar("DateTime")
    }
    assertEquals(schema.queryType, "Query")
    assertEquals(schema.mutationType, Some("Mutation"))
    assertEquals(schema.types.size, 7)
    assertEquals(schema.types(0).asInstanceOf[ObjectTypeOp].name, "Query")
    assertEquals(schema.types(0).asInstanceOf[ObjectTypeOp].fields(0).arguments.size, 1)
    assertEquals(schema.types(1).asInstanceOf[ObjectTypeOp].implementsInterfaces, List("Node"))
    assertEquals(schema.types(1).asInstanceOf[ObjectTypeOp].fields(2).isDeprecated, true)
    assertEquals(schema.types(2).asInstanceOf[InputObjectTypeOp].fields.size, 2)
    assertEquals(schema.types(3).asInstanceOf[InterfaceTypeOp].fields.size, 1)
    assertEquals(schema.types(4).asInstanceOf[UnionTypeOp].memberTypes, List("User", "Post"))
    assertEquals(schema.types(5).asInstanceOf[EnumTypeOp].values.size, 3)
    assertEquals(schema.types(6).asInstanceOf[ScalarTypeOp].name, "DateTime")

  test("DSL directive definitions"):
    val schema = GraphQlSchema.schema() { s =>
      s.objectType("Query") { obj =>
        obj.field("hello", TypeRef.STRING)()
      }
      s.directive(
        "cacheControl",
        List(DirectiveLocation.FieldDefinition, DirectiveLocation.Object),
        repeatable = false
      ) { d =>
        d.argument("maxAge", TypeRef.INT)
        d.argument("scope", TypeRef.OptionalType(TypeRef.NamedType("CacheScope")))
      }
    }
    assertEquals(schema.directives.size, 1)
    assertEquals(schema.directives(0).name, "cacheControl")
    assertEquals(schema.directives(0).locations.size, 2)
    assertEquals(schema.directives(0).arguments.size, 2)

  test("estimatedSize counts all descendants"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(
          GqlFieldOp("user", TypeRef.NamedType("User"),
            arguments = Vector(GqlArgumentOp("id", TypeRef.NamedType("ID"))))
        ))
      ),
      directives = Vector(
        DirectiveDefOp("deprecated", List(DirectiveLocation.FieldDefinition),
          arguments = Vector(GqlArgumentOp("reason", TypeRef.STRING)))
      )
    )
    // schema(1) + objectType(1) + field(1) + arg(1) + directive(1) + arg(1) = 6
    assert(schema.estimatedSize >= 6, s"estimatedSize was ${schema.estimatedSize}")

  test("mapChildren transforms fields"):
    val obj = ObjectTypeOp(
      "Test",
      fields = Vector(GqlFieldOp("old", TypeRef.INT))
    )
    val transformed = obj.mapChildren {
      case f: GqlFieldOp => f.copy(name = "new", regions = f.regions, attributes = f.attributes, span = f.span)
      case other         => other
    }
    assertEquals(transformed.asInstanceOf[ObjectTypeOp].fields(0).name, "new")
