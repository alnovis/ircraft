package io.alnovis.ircraft.core.framework

class NameConverterSuite extends munit.FunSuite:

  // -- snakeCase converter --

  test("snakeCase: camelCase from multi-word"):
    assertEquals(NameConverter.snakeCase.camelCase("required_int32"), "requiredInt32")

  test("snakeCase: pascalCase from multi-word"):
    assertEquals(NameConverter.snakeCase.pascalCase("required_int32"), "RequiredInt32")

  test("snakeCase: getterName from multi-word"):
    assertEquals(NameConverter.snakeCase.getterName("required_int32"), "getRequiredInt32")

  test("snakeCase: upperSnakeCase from multi-word"):
    assertEquals(NameConverter.snakeCase.upperSnakeCase("required_int32"), "REQUIRED_INT32")

  test("snakeCase: single word"):
    assertEquals(NameConverter.snakeCase.camelCase("id"), "id")
    assertEquals(NameConverter.snakeCase.pascalCase("id"), "Id")
    assertEquals(NameConverter.snakeCase.getterName("id"), "getId")
    assertEquals(NameConverter.snakeCase.upperSnakeCase("id"), "ID")

  test("snakeCase: hasMethodName"):
    assertEquals(NameConverter.snakeCase.hasMethodName("amount"), "hasAmount")

  test("snakeCase: setterName"):
    assertEquals(NameConverter.snakeCase.setterName("amount"), "setAmount")

  test("snakeCase: empty string"):
    assertEquals(NameConverter.snakeCase.camelCase(""), "")
    assertEquals(NameConverter.snakeCase.pascalCase(""), "")
    assertEquals(NameConverter.snakeCase.upperSnakeCase(""), "")

  // -- mixed converter --

  test("mixed: hyphen-separated"):
    assertEquals(NameConverter.mixed.camelCase("created-at"), "createdAt")
    assertEquals(NameConverter.mixed.pascalCase("created-at"), "CreatedAt")

  test("mixed: underscore-separated"):
    assertEquals(NameConverter.mixed.camelCase("user_name"), "userName")
    assertEquals(NameConverter.mixed.pascalCase("user_name"), "UserName")

  // -- identity converter --

  test("identity: returns name as-is"):
    assertEquals(NameConverter.identity.camelCase("someField"), "someField")
    assertEquals(NameConverter.identity.pascalCase("someField"), "someField")
    assertEquals(NameConverter.identity.getterName("someField"), "someField")
    assertEquals(NameConverter.identity.hasMethodName("someField"), "someField")
    assertEquals(NameConverter.identity.upperSnakeCase("someField"), "someField")

  test("snakeCase: leading/trailing underscores are ignored"):
    assertEquals(NameConverter.snakeCase.camelCase("_foo_bar_"), "fooBar")
    assertEquals(NameConverter.snakeCase.pascalCase("_foo_bar_"), "FooBar")
