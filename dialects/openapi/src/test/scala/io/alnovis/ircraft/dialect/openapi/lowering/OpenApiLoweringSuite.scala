package io.alnovis.ircraft.dialect.openapi.lowering

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.openapi.ops.*
import io.alnovis.ircraft.dialect.openapi.dsl.OpenApiSchema
import io.alnovis.ircraft.core.semantic.ops.*

class OpenApiLoweringSuite extends FunSuite:

  private def lower(ops: Operation*): IrModule =
    val module = IrModule("test", ops.toVector, AttributeMap.empty, None)
    OpenApiToSemanticLowering.run(module, PassContext()).module

  // -- Name utils ---------------------------------------------------------------

  test("toCamelCase"):
    assertEquals(OpenApiNameUtils.toCamelCase("user_name"), "userName")
    assertEquals(OpenApiNameUtils.toCamelCase("id"), "id")
    assertEquals(OpenApiNameUtils.toCamelCase("get-user"), "getUser")
    assertEquals(OpenApiNameUtils.toCamelCase("UserId"), "userId")

  test("toPascalCase"):
    assertEquals(OpenApiNameUtils.toPascalCase("user"), "User")
    assertEquals(OpenApiNameUtils.toPascalCase("user_name"), "UserName")
    assertEquals(OpenApiNameUtils.toPascalCase("get-user"), "GetUser")

  test("apiInterfaceName"):
    assertEquals(OpenApiNameUtils.apiInterfaceName(List("Users")), "UsersApi")
    assertEquals(OpenApiNameUtils.apiInterfaceName(List("pet-store")), "PetStoreApi")
    assertEquals(OpenApiNameUtils.apiInterfaceName(Nil), "Api")

  test("toEnumConstantName"):
    assertEquals(OpenApiNameUtils.toEnumConstantName("ACTIVE"), "ACTIVE")
    assertEquals(OpenApiNameUtils.toEnumConstantName("active"), "ACTIVE")
    assertEquals(OpenApiNameUtils.toEnumConstantName("inProgress"), "IN_PROGRESS")

  // -- Schema -> ClassOp --------------------------------------------------------

  test("schema object -> ClassOp with fields and getters"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(
        SchemaObjectOp("User", properties = Vector(
          SchemaPropertyOp("id", TypeRef.INT, required = true),
          SchemaPropertyOp("name", TypeRef.STRING, required = true),
          SchemaPropertyOp("email", TypeRef.STRING, required = false)
        ))
      )
    )
    val result = lower(spec)
    val fileOp = result.topLevel.head.asInstanceOf[FileOp]
    val classOp = fileOp.types.head.asInstanceOf[ClassOp]

    assertEquals(classOp.name, "User")
    assertEquals(classOp.fields.size, 3)
    assertEquals(classOp.fields(0).name, "id")
    assertEquals(classOp.fields(0).fieldType, TypeRef.INT)
    assertEquals(classOp.methods.size, 3)
    assertEquals(classOp.methods(0).name, "getId")
    assertEquals(classOp.methods(1).name, "getName")
    assertEquals(classOp.methods(2).name, "getEmail")

  test("schema object constructor includes only required fields"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(
        SchemaObjectOp("User", properties = Vector(
          SchemaPropertyOp("id", TypeRef.INT, required = true),
          SchemaPropertyOp("name", TypeRef.STRING, required = true),
          SchemaPropertyOp("email", TypeRef.STRING, required = false)
        ))
      )
    )
    val result = lower(spec)
    val classOp = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[ClassOp]

    assertEquals(classOp.constructors.size, 1)
    assertEquals(classOp.constructors(0).parameters.size, 2)
    assertEquals(classOp.constructors(0).parameters(0).name, "id")
    assertEquals(classOp.constructors(0).parameters(1).name, "name")

  test("schema object with no required fields has no constructor"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(
        SchemaObjectOp("Config", properties = Vector(
          SchemaPropertyOp("debug", TypeRef.BOOL, required = false)
        ))
      )
    )
    val result = lower(spec)
    val classOp = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[ClassOp]
    assertEquals(classOp.constructors.size, 0)

  // -- Enum -> EnumClassOp ------------------------------------------------------

  test("schema enum -> EnumClassOp with getValue"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(
        SchemaEnumOp("Status", TypeRef.STRING, values = Vector(
          SchemaEnumValueOp("active", "active"),
          SchemaEnumValueOp("inactive", "inactive")
        ))
      )
    )
    val result = lower(spec)
    val enumOp = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[EnumClassOp]

    assertEquals(enumOp.name, "Status")
    assertEquals(enumOp.constants.size, 2)
    assertEquals(enumOp.constants(0).name, "ACTIVE")
    assertEquals(enumOp.constants(1).name, "INACTIVE")
    assertEquals(enumOp.fields.size, 1)
    assertEquals(enumOp.fields(0).name, "value")
    assertEquals(enumOp.fields(0).fieldType, TypeRef.STRING)
    assertEquals(enumOp.constructors.size, 1)
    assert(enumOp.constructors(0).modifiers.contains(Modifier.Private))
    assertEquals(enumOp.methods.size, 1)
    assertEquals(enumOp.methods(0).name, "getValue")

  // -- Operations -> API InterfaceOp -------------------------------------------

  test("path operations -> API interface with methods"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users", operations = Vector(
          OperationOp(HttpMethod.Get, operationId = Some("listUsers"), tags = List("Users"),
            responses = Vector(ResponseOp("200", "OK", mediaTypes = Vector(
              MediaTypeOp("application/json", TypeRef.ListType(TypeRef.NamedType("User")))
            )))),
          OperationOp(HttpMethod.Post, operationId = Some("createUser"), tags = List("Users"),
            requestBodies = Vector(RequestBodyOp(required = true,
              mediaTypes = Vector(MediaTypeOp("application/json", TypeRef.NamedType("User"))))),
            responses = Vector(ResponseOp("201", "Created", mediaTypes = Vector(
              MediaTypeOp("application/json", TypeRef.NamedType("User"))
            )))))
        )
      )
    )
    val result = lower(spec)
    val fileOp = result.topLevel.collectFirst { case f: FileOp => f }.get
    val iface = fileOp.types.head.asInstanceOf[InterfaceOp]

    assertEquals(iface.name, "UsersApi")
    assertEquals(iface.methods.size, 2)
    assertEquals(iface.methods(0).name, "listUsers")
    assertEquals(iface.methods(0).returnType, TypeRef.ListType(TypeRef.NamedType("User")))
    assertEquals(iface.methods(1).name, "createUser")

  test("parameters become method params"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users/{id}", operations = Vector(
          OperationOp(HttpMethod.Get, operationId = Some("getUser"), tags = List("Users"),
            parameters = Vector(
              ParameterOp("id", ParameterLocation.Path, TypeRef.STRING, required = true),
              ParameterOp("fields", ParameterLocation.Query, TypeRef.STRING, required = false)
            ),
            responses = Vector(ResponseOp("200", "OK", mediaTypes = Vector(
              MediaTypeOp("application/json", TypeRef.NamedType("User"))
            ))))
        ))
      )
    )
    val result = lower(spec)
    val iface = result.topLevel.collectFirst { case f: FileOp => f }.get
      .types.head.asInstanceOf[InterfaceOp]

    val method = iface.methods(0)
    assertEquals(method.name, "getUser")
    assertEquals(method.parameters.size, 2)
    assertEquals(method.parameters(0).name, "id")
    assertEquals(method.parameters(0).paramType, TypeRef.STRING)
    assertEquals(method.parameters(1).name, "fields")

  test("request body becomes a method parameter"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users", operations = Vector(
          OperationOp(HttpMethod.Post, operationId = Some("createUser"), tags = List("Users"),
            requestBodies = Vector(RequestBodyOp(required = true,
              mediaTypes = Vector(MediaTypeOp("application/json", TypeRef.NamedType("CreateUserReq"))))),
            responses = Vector(ResponseOp("201", "Created")))
        ))
      )
    )
    val result = lower(spec)
    val iface = result.topLevel.collectFirst { case f: FileOp => f }.get
      .types.head.asInstanceOf[InterfaceOp]
    val method = iface.methods(0)
    assertEquals(method.parameters.size, 1)
    assertEquals(method.parameters(0).name, "body")
    assertEquals(method.parameters(0).paramType, TypeRef.NamedType("CreateUserReq"))

  test("response determines return type -- void when no media type"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/users/{id}", operations = Vector(
          OperationOp(HttpMethod.Delete, operationId = Some("deleteUser"), tags = List("Users"),
            responses = Vector(ResponseOp("204", "Deleted")))
        ))
      )
    )
    val result = lower(spec)
    val iface = result.topLevel.collectFirst { case f: FileOp => f }.get
      .types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.methods(0).returnType, TypeRef.VOID)

  test("deprecated operation gets annotation"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      paths = Vector(
        PathOp("/old", operations = Vector(
          OperationOp(HttpMethod.Get, operationId = Some("oldEndpoint"), tags = List("Legacy"),
            deprecated = true,
            responses = Vector(ResponseOp("200", "OK")))
        ))
      )
    )
    val result = lower(spec)
    val iface = result.topLevel.collectFirst { case f: FileOp => f }.get
      .types.head.asInstanceOf[InterfaceOp]
    assert(iface.methods(0).annotations.contains("Deprecated"))

  test("source provenance attribute on lowered ClassOp"):
    val spec = OpenApiSpecOp(
      "Test", "1.0.0",
      schemas = Vector(SchemaObjectOp("User"))
    )
    val result = lower(spec)
    val classOp = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[ClassOp]
    assertEquals(classOp.attributes.getString("ir.sourceNodeKind"), Some("openapi.schema_object"))

  // -- Full DSL -> lowering pipeline -------------------------------------------

  test("full DSL -> lowering pipeline"):
    val spec = OpenApiSchema.spec("Pet Store", "1.0.0", Some("A sample API")) { s =>
      s.server("https://api.example.com")
      s.tag("Pets")
      s.securityScheme("bearerAuth", SecuritySchemeType.Http)

      s.schema("Pet") { obj =>
        obj.property("id", TypeRef.INT, required = true)
        obj.property("name", TypeRef.STRING, required = true)
        obj.property("tag", TypeRef.STRING)
      }

      s.enum_("PetStatus") { e =>
        e.value("available")
        e.value("pending")
        e.value("sold")
      }

      s.path("/pets") { p =>
        p.get("listPets") { op =>
          op.tag("Pets")
          op.summary("List all pets")
          op.queryParam("limit", TypeRef.INT)
          op.response(200, "A list of pets", schemaType = TypeRef.ListType(TypeRef.NamedType("Pet")))
        }
        p.post("createPet") { op =>
          op.tag("Pets")
          op.requestBody("application/json", TypeRef.NamedType("Pet"))
          op.response(201, "Pet created", schemaType = TypeRef.NamedType("Pet"))
        }
      }
    }

    val module = IrModule("test", Vector(spec), AttributeMap.empty, None)
    val result = OpenApiToSemanticLowering.run(module, PassContext())

    assert(result.isSuccess)
    // Should produce: Pet ClassOp file, PetStatus EnumClassOp file, PetsApi InterfaceOp file
    assertEquals(result.module.topLevel.size, 3)
    assert(result.module.topLevel.forall(_.isInstanceOf[FileOp]))

    val petFile = result.module.topLevel(0).asInstanceOf[FileOp]
    val petClass = petFile.types.head.asInstanceOf[ClassOp]
    assertEquals(petClass.name, "Pet")
    assertEquals(petClass.fields.size, 3)
    assertEquals(petClass.methods.size, 3)

    val enumFile = result.module.topLevel(1).asInstanceOf[FileOp]
    val petStatusEnum = enumFile.types.head.asInstanceOf[EnumClassOp]
    assertEquals(petStatusEnum.name, "PetStatus")
    assertEquals(petStatusEnum.constants.size, 3)

    val apiFile = result.module.topLevel(2).asInstanceOf[FileOp]
    val apiIface = apiFile.types.head.asInstanceOf[InterfaceOp]
    assertEquals(apiIface.name, "PetsApi")
    assertEquals(apiIface.methods.size, 2)

  test("non-openapi operations pass through unchanged"):
    val existing = InterfaceOp(name = "Existing", methods = Vector(
      MethodOp("foo", TypeRef.VOID)
    ))
    val spec = OpenApiSpecOp("Test", "1.0.0",
      schemas = Vector(SchemaObjectOp("Item"))
    )
    val module = IrModule("test", Vector(existing, spec), AttributeMap.empty, None)
    val result = OpenApiToSemanticLowering.run(module, PassContext())

    assert(result.module.topLevel(0).isInstanceOf[InterfaceOp])
    assertEquals(result.module.topLevel(0).asInstanceOf[InterfaceOp].name, "Existing")
    assert(result.module.topLevel(1).isInstanceOf[FileOp])
