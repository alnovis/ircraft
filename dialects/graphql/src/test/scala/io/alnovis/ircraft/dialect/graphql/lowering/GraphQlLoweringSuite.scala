package io.alnovis.ircraft.dialect.graphql.lowering

import munit.FunSuite

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.graphql.ops.*
import io.alnovis.ircraft.dialect.graphql.dsl.GraphQlSchema
import io.alnovis.ircraft.core.semantic.ops.*

class GraphQlLoweringSuite extends FunSuite:

  private def lower(ops: Operation*): IrModule =
    val module = IrModule("test", ops.toVector, AttributeMap.empty, None)
    GraphQlToSemanticLowering.run(module, PassContext()).module

  // -- ObjectType -> InterfaceOp -----------------------------------------------

  test("ObjectType -> InterfaceOp with getters"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("User", fields = Vector(
          GqlFieldOp("id", TypeRef.NamedType("ID")),
          GqlFieldOp("name", TypeRef.STRING),
          GqlFieldOp("email", TypeRef.OptionalType(TypeRef.STRING))
        ))
      )
    )
    val result = lower(schema)
    val fileOp = result.topLevel.head.asInstanceOf[FileOp]
    val iface = fileOp.types.head.asInstanceOf[InterfaceOp]

    assertEquals(iface.name, "User")
    assertEquals(iface.methods.size, 3)
    assertEquals(iface.methods(0).name, "id")
    assertEquals(iface.methods(0).returnType, TypeRef.NamedType("ID"))
    assertEquals(iface.methods(1).name, "name")
    assertEquals(iface.methods(1).returnType, TypeRef.STRING)
    assertEquals(iface.methods(2).name, "email")
    assertEquals(iface.methods(2).returnType, TypeRef.OptionalType(TypeRef.STRING))
    assert(iface.methods.forall(_.modifiers.contains(Modifier.Abstract)))

  test("ObjectType with implements -> InterfaceOp with extendsTypes"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Cat", implementsInterfaces = List("Animal", "Pet"), fields = Vector(
          GqlFieldOp("name", TypeRef.STRING)
        ))
      )
    )
    val result = lower(schema)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.extendsTypes, List(TypeRef.NamedType("Animal"), TypeRef.NamedType("Pet")))

  // -- InputObjectType -> ClassOp ----------------------------------------------

  test("InputObjectType -> ClassOp with fields and constructor"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        InputObjectTypeOp("CreateUserInput", fields = Vector(
          InputFieldOp("name", TypeRef.STRING),
          InputFieldOp("email", TypeRef.STRING),
          InputFieldOp("age", TypeRef.OptionalType(TypeRef.INT), defaultValue = Some("18"))
        ))
      )
    )
    val result = lower(schema)
    val fileOp = result.topLevel.head.asInstanceOf[FileOp]
    val classOp = fileOp.types.head.asInstanceOf[ClassOp]

    assertEquals(classOp.name, "CreateUserInput")
    assertEquals(classOp.fields.size, 3)
    assertEquals(classOp.fields(0).name, "name")
    assertEquals(classOp.fields(0).fieldType, TypeRef.STRING)
    assertEquals(classOp.fields(2).defaultValue, Some("18"))
    assertEquals(classOp.constructors.size, 1)
    assertEquals(classOp.constructors(0).parameters.size, 3)
    assertEquals(classOp.methods.size, 3)
    assertEquals(classOp.methods(0).name, "name")

  // -- InterfaceType -> InterfaceOp -------------------------------------------

  test("InterfaceType -> InterfaceOp (abstract)"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        InterfaceTypeOp("Node", fields = Vector(
          GqlFieldOp("id", TypeRef.NamedType("ID"))
        ))
      )
    )
    val result = lower(schema)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.name, "Node")
    assertEquals(iface.methods.size, 1)
    assertEquals(iface.methods(0).name, "id")
    assert(iface.methods(0).modifiers.contains(Modifier.Abstract))

  // -- EnumType -> EnumClassOp ------------------------------------------------

  test("EnumType -> EnumClassOp"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        EnumTypeOp("Status", values = Vector(
          GqlEnumValueOp("ACTIVE"),
          GqlEnumValueOp("INACTIVE"),
          GqlEnumValueOp("DELETED", isDeprecated = true, deprecationReason = Some("Use INACTIVE"))
        ))
      )
    )
    val result = lower(schema)
    val enumOp = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[EnumClassOp]

    assertEquals(enumOp.name, "Status")
    assertEquals(enumOp.constants.size, 3)
    assertEquals(enumOp.constants(0).name, "ACTIVE")
    assertEquals(enumOp.constants(1).name, "INACTIVE")
    assertEquals(enumOp.constants(2).name, "DELETED")
    assertEquals(enumOp.constants(2).attributes.getBool("graphql.isDeprecated"), Some(true))
    assertEquals(enumOp.constants(2).attributes.getString("graphql.deprecationReason"), Some("Use INACTIVE"))

  // -- Field with arguments -> MethodOp with Parameters -----------------------

  test("field with arguments -> MethodOp with Parameters"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(
          GqlFieldOp("users", TypeRef.ListType(TypeRef.NamedType("User")),
            arguments = Vector(
              GqlArgumentOp("first", TypeRef.OptionalType(TypeRef.INT)),
              GqlArgumentOp("after", TypeRef.OptionalType(TypeRef.STRING))
            )
          )
        ))
      )
    )
    val result = lower(schema)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val method = iface.methods(0)
    assertEquals(method.name, "users")
    assertEquals(method.returnType, TypeRef.ListType(TypeRef.NamedType("User")))
    assertEquals(method.parameters.size, 2)
    assertEquals(method.parameters(0).name, "first")
    assertEquals(method.parameters(0).paramType, TypeRef.OptionalType(TypeRef.INT))
    assertEquals(method.parameters(1).name, "after")

  // -- UnionType -> sealed InterfaceOp ----------------------------------------

  test("UnionType -> sealed InterfaceOp"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        UnionTypeOp("SearchResult", List("User", "Post", "Comment"))
      )
    )
    val result = lower(schema)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.name, "SearchResult")
    assert(iface.annotations.contains("Sealed"))
    assertEquals(iface.methods.size, 0)

  // -- ScalarType -> ignored --------------------------------------------------

  test("ScalarType is ignored in lowering"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ScalarTypeOp("DateTime"),
        ObjectTypeOp("Query", fields = Vector(GqlFieldOp("now", TypeRef.NamedType("DateTime"))))
      )
    )
    val result = lower(schema)
    // Only the ObjectType is lowered, ScalarType is skipped
    assertEquals(result.topLevel.size, 1)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.name, "Query")

  // -- Deprecated field -> annotated method -----------------------------------

  test("deprecated field -> method with Deprecated annotation"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(
          GqlFieldOp("oldField", TypeRef.STRING, isDeprecated = true, deprecationReason = Some("Use newField"))
        ))
      )
    )
    val result = lower(schema)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    val method = iface.methods(0)
    assert(method.annotations.contains("Deprecated"))
    assertEquals(method.attributes.getBool("graphql.isDeprecated"), Some(true))
    assertEquals(method.attributes.getString("graphql.deprecationReason"), Some("Use newField"))

  // -- Source provenance ------------------------------------------------------

  test("source provenance attribute on lowered InterfaceOp"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(GqlFieldOp("hello", TypeRef.STRING)))
      )
    )
    val result = lower(schema)
    val iface = result.topLevel.head.asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(iface.attributes.getString("ir.sourceNodeKind"), Some("graphql.object_type"))

  test("schema attribute on FileOp"):
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(
        ObjectTypeOp("Query", fields = Vector(GqlFieldOp("hello", TypeRef.STRING)))
      )
    )
    val result = lower(schema)
    val fileOp = result.topLevel.head.asInstanceOf[FileOp]
    assertEquals(fileOp.attributes.getString("graphql.queryType"), Some("Query"))

  // -- Full DSL -> lowering pipeline ------------------------------------------

  test("full DSL -> lowering pipeline"):
    val schema = GraphQlSchema.schema("Query") { s =>
      s.mutationType("Mutation")
      s.objectType("Query") { obj =>
        obj.field("user", TypeRef.OptionalType(TypeRef.NamedType("User"))) { f =>
          f.argument("id", TypeRef.NamedType("ID"))
        }
        obj.field("users", TypeRef.ListType(TypeRef.NamedType("User")))()
      }
      s.objectType("User", implements = List("Node")) { obj =>
        obj.field("id", TypeRef.NamedType("ID"))()
        obj.field("name", TypeRef.STRING)()
        obj.field("email", TypeRef.OptionalType(TypeRef.STRING))()
      }
      s.inputType("CreateUserInput") { inp =>
        inp.field("name", TypeRef.STRING)
        inp.field("email", TypeRef.STRING)
      }
      s.enumType("Role") { e =>
        e.value("ADMIN")
        e.value("USER")
      }
      s.unionType("SearchResult", List("User", "Post"))
      s.scalar("DateTime")
    }

    val module = IrModule("test", Vector(schema), AttributeMap.empty, None)
    val result = GraphQlToSemanticLowering.run(module, PassContext())

    assert(result.isSuccess)
    // Query InterfaceOp + User InterfaceOp + CreateUserInput ClassOp + Role EnumClassOp + SearchResult InterfaceOp
    // ScalarType is skipped
    assertEquals(result.module.topLevel.size, 5)
    assert(result.module.topLevel.forall(_.isInstanceOf[FileOp]))

    val queryIface = result.module.topLevel(0).asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(queryIface.name, "Query")
    assertEquals(queryIface.methods.size, 2)
    assertEquals(queryIface.methods(0).parameters.size, 1)

    val userIface = result.module.topLevel(1).asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(userIface.name, "User")
    assertEquals(userIface.extendsTypes, List(TypeRef.NamedType("Node")))

    val inputClass = result.module.topLevel(2).asInstanceOf[FileOp].types.head.asInstanceOf[ClassOp]
    assertEquals(inputClass.name, "CreateUserInput")
    assertEquals(inputClass.fields.size, 2)
    assertEquals(inputClass.constructors.size, 1)

    val roleEnum = result.module.topLevel(3).asInstanceOf[FileOp].types.head.asInstanceOf[EnumClassOp]
    assertEquals(roleEnum.name, "Role")
    assertEquals(roleEnum.constants.size, 2)

    val searchIface = result.module.topLevel(4).asInstanceOf[FileOp].types.head.asInstanceOf[InterfaceOp]
    assertEquals(searchIface.name, "SearchResult")
    assert(searchIface.annotations.contains("Sealed"))

  test("non-graphql operations pass through unchanged"):
    val existing = InterfaceOp(name = "Existing", methods = Vector(
      MethodOp("foo", TypeRef.VOID)
    ))
    val schema = GqlSchemaOp(
      "Query",
      types = Vector(ObjectTypeOp("Query", fields = Vector(GqlFieldOp("hello", TypeRef.STRING))))
    )
    val module = IrModule("test", Vector(existing, schema), AttributeMap.empty, None)
    val result = GraphQlToSemanticLowering.run(module, PassContext())

    assert(result.module.topLevel(0).isInstanceOf[InterfaceOp])
    assertEquals(result.module.topLevel(0).asInstanceOf[InterfaceOp].name, "Existing")
    assert(result.module.topLevel(1).isInstanceOf[FileOp])
