package io.alnovis.ircraft.dialect.openapi

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.ops.*
import io.alnovis.ircraft.dialect.openapi.dsl.OpenApiSchema

class OpenApiDialectSuite extends FunSuite:

  test("build a simple schema object with properties"):
    val schema = SchemaObjectOp(
      "User",
      properties = Vector(
        SchemaPropertyOp("id", TypeRef.INT, required = true),
        SchemaPropertyOp("name", TypeRef.STRING, required = true),
        SchemaPropertyOp("email", TypeRef.STRING, required = false)
      )
    )
    assertEquals(schema.name, "User")
    assertEquals(schema.properties.size, 3)
    assertEquals(schema.properties(0).name, "id")
    assertEquals(schema.properties(0).required, true)
    assertEquals(schema.properties(2).required, false)

  test("build schema enum with values"):
    val enumOp = SchemaEnumOp(
      "Status",
      TypeRef.STRING,
      values = Vector(
        SchemaEnumValueOp("active", "active"),
        SchemaEnumValueOp("inactive", "inactive")
      )
    )
    assertEquals(enumOp.name, "Status")
    assertEquals(enumOp.baseType, TypeRef.STRING)
    assertEquals(enumOp.values.size, 2)
    assertEquals(enumOp.values(0).name, "active")

  test("build path with operations"):
    val path = PathOp(
      "/users/{id}",
      operations = Vector(
        OperationOp(
          HttpMethod.Get,
          operationId = Some("getUser"),
          tags = List("Users"),
          responses = Vector(
            ResponseOp("200", "Success", mediaTypes = Vector(
              MediaTypeOp("application/json", TypeRef.NamedType("User"))
            ))
          )
        )
      )
    )
    assertEquals(path.pathPattern, "/users/{id}")
    assertEquals(path.operations.size, 1)
    assertEquals(path.operations(0).httpMethod, HttpMethod.Get)
    assertEquals(path.operations(0).operationId, Some("getUser"))

  test("build operation with parameters"):
    val op = OperationOp(
      HttpMethod.Get,
      operationId = Some("listUsers"),
      parameters = Vector(
        ParameterOp("limit", ParameterLocation.Query, TypeRef.INT, required = false),
        ParameterOp("offset", ParameterLocation.Query, TypeRef.INT, required = false)
      ),
      responses = Vector(ResponseOp("200", "Success"))
    )
    assertEquals(op.parameters.size, 2)
    assertEquals(op.parameters(0).name, "limit")
    assertEquals(op.parameters(0).location, ParameterLocation.Query)

  test("build operation with request body"):
    val op = OperationOp(
      HttpMethod.Post,
      operationId = Some("createUser"),
      requestBodies = Vector(
        RequestBodyOp(
          required = true,
          mediaTypes = Vector(MediaTypeOp("application/json", TypeRef.NamedType("CreateUserRequest")))
        )
      ),
      responses = Vector(ResponseOp("201", "Created"))
    )
    assertEquals(op.requestBodies.size, 1)
    assertEquals(op.requestBodies(0).required, true)
    assertEquals(op.requestBodies(0).mediaTypes(0).schemaRef, TypeRef.NamedType("CreateUserRequest"))

  test("build spec with all components"):
    val spec = OpenApiSpecOp(
      title = "Pet Store",
      version = "1.0.0",
      description = Some("A sample API"),
      servers = Vector(ServerOp("https://api.example.com")),
      tags = Vector(TagOp("Pets", Some("Pet operations"))),
      securitySchemes = Vector(
        SecuritySchemeOp("bearerAuth", SecuritySchemeType.Http, details = Map("scheme" -> "bearer"))
      ),
      schemas = Vector(SchemaObjectOp("Pet", properties = Vector(
        SchemaPropertyOp("name", TypeRef.STRING, required = true)
      ))),
      paths = Vector(PathOp("/pets", operations = Vector(
        OperationOp(HttpMethod.Get, operationId = Some("listPets"),
          responses = Vector(ResponseOp("200", "Success")))
      )))
    )
    assertEquals(spec.title, "Pet Store")
    assertEquals(spec.version, "1.0.0")
    assertEquals(spec.description, Some("A sample API"))
    assertEquals(spec.servers.size, 1)
    assertEquals(spec.tags.size, 1)
    assertEquals(spec.securitySchemes.size, 1)
    assertEquals(spec.schemas.size, 1)
    assertEquals(spec.paths.size, 1)

  test("build composition with oneOf"):
    val comp = SchemaCompositionOp(
      CompositionKind.OneOf,
      discriminatorProperty = Some("type"),
      discriminatorMapping = Map("cat" -> "#/components/schemas/Cat"),
      schemas = Vector(
        SchemaObjectOp("Cat", properties = Vector(SchemaPropertyOp("meow", TypeRef.BOOL, required = false))),
        SchemaObjectOp("Dog", properties = Vector(SchemaPropertyOp("bark", TypeRef.BOOL, required = false)))
      )
    )
    assertEquals(comp.compositionKind, CompositionKind.OneOf)
    assertEquals(comp.discriminatorProperty, Some("type"))
    assertEquals(comp.schemas.size, 2)

  test("content hash is deterministic"):
    val p1 = SchemaPropertyOp("name", TypeRef.STRING, required = true)
    val p2 = SchemaPropertyOp("name", TypeRef.STRING, required = true)
    assertEquals(p1.contentHash, p2.contentHash)

  test("content hash differs for different properties"):
    val p1 = SchemaPropertyOp("name", TypeRef.STRING, required = true)
    val p2 = SchemaPropertyOp("name", TypeRef.STRING, required = false)
    assertNotEquals(p1.contentHash, p2.contentHash)

  test("content hash differs for different types"):
    val p1 = SchemaPropertyOp("value", TypeRef.INT, required = false)
    val p2 = SchemaPropertyOp("value", TypeRef.STRING, required = false)
    assertNotEquals(p1.contentHash, p2.contentHash)

  test("OpenApiDialect owns openapi operations"):
    val prop = SchemaPropertyOp("x", TypeRef.INT, required = false)
    assert(OpenApiDialect.owns(prop))
    assertEquals(prop.dialectName, "openapi")
    assertEquals(prop.opName, "schema_property")

  test("DSL builds correct spec"):
    val spec = OpenApiSchema.spec("Pet Store", "1.0.0") { s =>
      s.server("https://api.example.com")
      s.tag("Pets", Some("Pet operations"))
      s.schema("Pet") { obj =>
        obj.property("id", TypeRef.INT, required = true)
        obj.property("name", TypeRef.STRING, required = true)
        obj.property("tag", TypeRef.STRING)
      }
      s.enum_("Status") { e =>
        e.value("available")
        e.value("pending")
        e.value("sold")
      }
      s.path("/pets") { p =>
        p.get("listPets") { op =>
          op.tag("Pets")
          op.summary("List all pets")
          op.queryParam("limit", TypeRef.INT)
          op.response(200, "A list of pets", schemaType = TypeRef.NamedType("Pet"))
        }
        p.post("createPet") { op =>
          op.tag("Pets")
          op.requestBody("application/json", TypeRef.NamedType("Pet"))
          op.response(201, "Created")
        }
      }
    }
    assertEquals(spec.title, "Pet Store")
    assertEquals(spec.servers.size, 1)
    assertEquals(spec.tags.size, 1)
    assertEquals(spec.schemas.size, 2)
    assertEquals(spec.paths.size, 1)
    assertEquals(spec.paths(0).operations.size, 2)

  test("DSL with all operation types"):
    val spec = OpenApiSchema.spec("Test", "1.0.0") { s =>
      s.path("/items/{id}") { p =>
        p.get("getItem") { op =>
          op.pathParam("id", TypeRef.STRING)
          op.response(200, "OK")
        }
        p.put("updateItem") { op =>
          op.pathParam("id", TypeRef.STRING)
          op.requestBody("application/json", TypeRef.NamedType("Item"))
          op.response(200, "Updated")
        }
        p.delete("deleteItem") { op =>
          op.pathParam("id", TypeRef.STRING)
          op.response(204, "Deleted")
        }
        p.patch("patchItem") { op =>
          op.pathParam("id", TypeRef.STRING)
          op.response(200, "Patched")
        }
      }
    }
    val ops = spec.paths(0).operations
    assertEquals(ops.size, 4)
    assertEquals(ops(0).httpMethod, HttpMethod.Get)
    assertEquals(ops(1).httpMethod, HttpMethod.Put)
    assertEquals(ops(2).httpMethod, HttpMethod.Delete)
    assertEquals(ops(3).httpMethod, HttpMethod.Patch)

  test("DSL with security scheme and composition"):
    val spec = OpenApiSchema.spec("Secure API", "2.0.0") { s =>
      s.securityScheme("bearerAuth", SecuritySchemeType.Http, Map("scheme" -> "bearer"))
      s.composition(CompositionKind.OneOf) { c =>
        c.discriminator("petType", Map("cat" -> "Cat", "dog" -> "Dog"))
        c.schema("Cat") { obj =>
          obj.property("meow", TypeRef.BOOL)
        }
        c.schemaRef("Dog")
      }
    }
    assertEquals(spec.securitySchemes.size, 1)
    assertEquals(spec.securitySchemes(0).name, "bearerAuth")
    assertEquals(spec.schemas.size, 1)
    val comp = spec.schemas(0).asInstanceOf[SchemaCompositionOp]
    assertEquals(comp.compositionKind, CompositionKind.OneOf)
    assertEquals(comp.discriminatorProperty, Some("petType"))
    assertEquals(comp.schemas.size, 2)

  test("estimatedSize counts all descendants"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(
        SchemaObjectOp("User", properties = Vector(
          SchemaPropertyOp("id", TypeRef.INT, required = false),
          SchemaPropertyOp("name", TypeRef.STRING, required = false)
        ))
      ),
      paths = Vector(
        PathOp("/users", operations = Vector(
          OperationOp(HttpMethod.Get, responses = Vector(ResponseOp("200", "OK")))
        ))
      )
    )
    // spec(1) + schemaObj(1) + 2 props(2) + path(1) + operation(1) + response(1) = 7
    assert(spec.estimatedSize >= 7, s"estimatedSize was ${spec.estimatedSize}")

  test("DSL operation deprecated flag"):
    val spec = OpenApiSchema.spec("Test", "1.0.0") { s =>
      s.path("/old") { p =>
        p.get("oldEndpoint") { op =>
          op.deprecated()
          op.response(200, "OK")
        }
      }
    }
    assert(spec.paths(0).operations(0).deprecated)

  test("field types: list, optional, named"):
    val schema = SchemaObjectOp(
      "Container",
      properties = Vector(
        SchemaPropertyOp("tags", TypeRef.ListType(TypeRef.STRING), required = false),
        SchemaPropertyOp("metadata", TypeRef.MapType(TypeRef.STRING, TypeRef.STRING), required = false),
        SchemaPropertyOp("child", TypeRef.NamedType("Child"), required = false),
        SchemaPropertyOp("nickname", TypeRef.OptionalType(TypeRef.STRING), required = false)
      )
    )
    assertEquals(schema.properties(0).fieldType, TypeRef.ListType(TypeRef.STRING))
    assertEquals(schema.properties(1).fieldType, TypeRef.MapType(TypeRef.STRING, TypeRef.STRING))
    assertEquals(schema.properties(2).fieldType, TypeRef.NamedType("Child"))
    assertEquals(schema.properties(3).fieldType, TypeRef.OptionalType(TypeRef.STRING))
