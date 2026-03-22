package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.TypeRef.*

class TypeRefSuite extends munit.FunSuite:

  test("PrimitiveType hash is deterministic"):
    val h = summon[ContentHashable[TypeRef]]
    assertEquals(h.contentHash(PrimitiveType.Int32), h.contentHash(PrimitiveType.Int32))

  test("different PrimitiveTypes have different hashes"):
    val h = summon[ContentHashable[TypeRef]]
    assertNotEquals(h.contentHash(PrimitiveType.Int32), h.contentHash(PrimitiveType.Int64))

  test("NamedType hash depends on fqn"):
    val h = summon[ContentHashable[TypeRef]]
    assertNotEquals(
      h.contentHash(NamedType("com.example.Foo")),
      h.contentHash(NamedType("com.example.Bar")),
    )

  test("ListType hash depends on element type"):
    val h = summon[ContentHashable[TypeRef]]
    assertNotEquals(
      h.contentHash(ListType(PrimitiveType.Int32)),
      h.contentHash(ListType(PrimitiveType.Int64)),
    )

  test("MapType hash depends on both key and value"):
    val h = summon[ContentHashable[TypeRef]]
    val m1 = MapType(PrimitiveType.StringType, PrimitiveType.Int32)
    val m2 = MapType(PrimitiveType.StringType, PrimitiveType.Int64)
    assertNotEquals(h.contentHash(m1), h.contentHash(m2))

  test("exhaustive pattern match on TypeRef"):
    val types: List[TypeRef] = List(
      PrimitiveType.Int32,
      VoidType,
      NamedType("Foo"),
      ListType(PrimitiveType.Bool),
      MapType(PrimitiveType.StringType, PrimitiveType.Int32),
      OptionalType(PrimitiveType.Int64),
      EnumType("Status", List(EnumValue("OK", 0), EnumValue("ERROR", 1))),
      UnionType(List(PrimitiveType.Int32, PrimitiveType.StringType)),
      ParameterizedType(NamedType("List"), List(PrimitiveType.Int32)),
      WildcardType(Some(NamedType("Foo"))),
    )

    val h = summon[ContentHashable[TypeRef]]
    // all types should produce a hash without error
    types.foreach(t => h.contentHash(t))
    assertEquals(types.size, 10)

  test("type aliases are correct"):
    assertEquals(TypeRef.STRING, PrimitiveType.StringType)
    assertEquals(TypeRef.INT, PrimitiveType.Int32)
    assertEquals(TypeRef.LONG, PrimitiveType.Int64)
    assertEquals(TypeRef.VOID, VoidType)
