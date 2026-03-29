package io.alnovis.ircraft.core.serde

import io.alnovis.ircraft.core.*

class IrParserSuite extends munit.FunSuite:

  // -- Basic parsing --------------------------------------------------------

  test("parse empty module"):
    val text = """module "empty" {
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight, s"Parse failed: ${result.left.getOrElse("")}")
    assertEquals(result.toOption.get.name, "empty")
    assertEquals(result.toOption.get.topLevel.size, 0)

  test("parse single leaf op"):
    val text = """module "test" {
                 |  test.entry [name="hello", count=42]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    val op = result.toOption.get.topLevel.head
    assertEquals(op.kind, NodeKind("test", "entry"))
    assertEquals(op.attributes.getString("name"), Some("hello"))
    assertEquals(op.attributes.getInt("count"), Some(42))

  test("parse container with regions"):
    val text = """module "test" {
                 |  test.root [name="r"] {
                 |    items {
                 |      test.leaf [v="a"]
                 |      test.leaf [v="b"]
                 |    }
                 |  }
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    val root = result.toOption.get.topLevel.head
    assertEquals(root.regions.size, 1)
    assertEquals(root.regions.head.name, "items")
    assertEquals(root.regions.head.operations.size, 2)

  // -- All attribute types --------------------------------------------------

  test("parse string attribute with escapes"):
    val text = """module "test" {
                 |  test.op [s="line1\nline2\t\"quoted\"\\back"]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    assertEquals(result.toOption.get.topLevel.head.attributes.getString("s"), Some("line1\nline2\t\"quoted\"\\back"))

  test("parse long attribute"):
    val text = """module "test" {
                 |  test.op [big=9999999999L]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    assertEquals(result.toOption.get.topLevel.head.attributes.getLong("big"), Some(9999999999L))

  test("parse bool attribute"):
    val text = """module "test" {
                 |  test.op [active=true, deleted=false]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    assertEquals(result.toOption.get.topLevel.head.attributes.getBool("active"), Some(true))
    assertEquals(result.toOption.get.topLevel.head.attributes.getBool("deleted"), Some(false))

  test("parse string list attribute"):
    val text = """module "test" {
                 |  test.op [tags=["a","b","c"]]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    assertEquals(result.toOption.get.topLevel.head.attributes.getStringList("tags"), Some(List("a", "b", "c")))

  test("parse int list attribute"):
    val text = """module "test" {
                 |  test.op [nums=[1,2,3]]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    val attr = result.toOption.get.topLevel.head.attributes.getAs[Attribute.IntListAttr]("nums")
    assertEquals(attr.map(_.values), Some(List(1, 2, 3)))

  test("parse ref attribute"):
    val text = """module "test" {
                 |  test.op [target=@12345]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    val attr = result.toOption.get.topLevel.head.attributes.getAs[Attribute.RefAttr]("target")
    assertEquals(attr.map(_.target), Some(NodeId(12345)))

  test("parse attr map attribute"):
    val text = """module "test" {
                 |  test.op [cfg={host="localhost", port=8080}]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    val attr = result.toOption.get.topLevel.head.attributes.getAs[Attribute.AttrMapAttr]("cfg")
    assert(attr.isDefined)
    assertEquals(attr.get.values.size, 2)

  // -- Round-trip with IrPrinter --------------------------------------------

  test("round-trip: print -> parse -> print"):
    val leaf1 = GenericOp(
      NodeKind("test", "entry"),
      AttributeMap(Attribute.StringAttr("key", "host"), Attribute.StringAttr("value", "localhost"))
    )
    val leaf2 = GenericOp(
      NodeKind("test", "entry"),
      AttributeMap(Attribute.StringAttr("key", "port"), Attribute.IntAttr("value", 8080))
    )
    val root = GenericOp(
      NodeKind("test", "section"),
      AttributeMap(Attribute.StringAttr("name", "server")),
      Vector(Region("entries", Vector(leaf1, leaf2)))
    )
    val module = IrModule("my-project", Vector(root))

    val printed1 = IrPrinter.print(module)
    val parsed   = IrParser.parse(printed1)
    assert(parsed.isRight, s"Parse failed: ${parsed.left.getOrElse("")}")
    val printed2 = IrPrinter.print(parsed.toOption.get)
    assertEquals(printed2, printed1)

  test("round-trip with all attribute types"):
    val op = GenericOp(
      NodeKind("test", "all"),
      AttributeMap(
        Attribute.StringAttr("s", "text"),
        Attribute.IntAttr("i", -42),
        Attribute.LongAttr("l", 999999999999L),
        Attribute.BoolAttr("b", true),
        Attribute.StringListAttr("sl", List("x", "y")),
        Attribute.IntListAttr("il", List(1, 2)),
        Attribute.RefAttr("r", NodeId(777)),
        Attribute.AttrMapAttr("m", Map("a" -> Attribute.IntAttr("a", 1)))
      )
    )
    val module   = IrModule("test", Vector(op))
    val printed1 = IrPrinter.print(module)
    val parsed   = IrParser.parse(printed1)
    assert(parsed.isRight, s"Parse failed: ${parsed.left.getOrElse("")}\n\nInput:\n$printed1")
    val printed2 = IrPrinter.print(parsed.toOption.get)
    assertEquals(printed2, printed1)

  test("round-trip deeply nested"):
    val l3       = GenericOp(NodeKind("t", "leaf"), AttributeMap(Attribute.StringAttr("d", "deep")))
    val l2       = GenericOp(NodeKind("t", "mid"), regions = Vector(Region("inner", Vector(l3))))
    val l1       = GenericOp(NodeKind("t", "outer"), regions = Vector(Region("body", Vector(l2))))
    val module   = IrModule("deep", Vector(l1))
    val printed1 = IrPrinter.print(module)
    val parsed   = IrParser.parse(printed1)
    assert(parsed.isRight)
    assertEquals(IrPrinter.print(parsed.toOption.get), printed1)

  // -- Error cases ----------------------------------------------------------

  test("error: missing closing brace"):
    val text = """module "test" {
                 |  test.op [name="x"]
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isLeft)

  test("error: malformed attribute"):
    val text = """module "test" {
                 |  test.op [= broken]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isLeft)
    val err = result.left.toOption.get
    assert(err.line > 0)
    assert(err.column > 0)

  // -- Comments -------------------------------------------------------------

  test("parse with line comments"):
    val text = """// this is a comment
                 |module "test" {
                 |  // another comment
                 |  test.op [name="x"]
                 |}
                 |""".stripMargin
    val result = IrParser.parse(text)
    assert(result.isRight)
    assertEquals(result.toOption.get.topLevel.size, 1)
