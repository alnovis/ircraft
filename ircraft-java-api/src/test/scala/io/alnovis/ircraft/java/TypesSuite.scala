package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.TypeRef

class TypesSuite extends munit.FunSuite:

  test("primitive constants match TypeRef"):
    assertEquals(Types.STRING, TypeRef.STRING)
    assertEquals(Types.INT, TypeRef.INT)
    assertEquals(Types.LONG, TypeRef.LONG)
    assertEquals(Types.FLOAT, TypeRef.FLOAT)
    assertEquals(Types.DOUBLE, TypeRef.DOUBLE)
    assertEquals(Types.BOOL, TypeRef.BOOL)
    assertEquals(Types.BYTES, TypeRef.BYTES)
    assertEquals(Types.VOID, TypeRef.VOID)

  test("named creates NamedType"):
    assertEquals(Types.named("com.example.Money"), TypeRef.NamedType("com.example.Money"))

  test("list creates ListType"):
    assertEquals(Types.list(Types.STRING), TypeRef.ListType(TypeRef.STRING))

  test("map creates MapType"):
    assertEquals(Types.map(Types.STRING, Types.INT), TypeRef.MapType(TypeRef.STRING, TypeRef.INT))

  test("optional creates OptionalType"):
    assertEquals(Types.optional(Types.STRING), TypeRef.OptionalType(TypeRef.STRING))
