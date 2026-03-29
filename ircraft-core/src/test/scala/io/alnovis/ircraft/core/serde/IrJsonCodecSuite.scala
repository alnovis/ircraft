package io.alnovis.ircraft.core.serde

import io.alnovis.ircraft.core.*

class IrJsonCodecSuite extends munit.FunSuite:

  test("round-trip empty module"):
    val module = IrModule("empty", Vector.empty)
    val json   = IrJsonCodec.toJson(module)
    val parsed = IrJsonCodec.fromJson(json)
    assertEquals(parsed.name, "empty")
    assertEquals(parsed.topLevel.size, 0)

  test("round-trip single leaf with string and int attrs"):
    val op = GenericOp(
      NodeKind("test", "entry"),
      AttributeMap(Attribute.StringAttr("name", "hello"), Attribute.IntAttr("count", 42))
    )
    val module = IrModule("test", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    assertEquals(parsed.topLevel.size, 1)
    assertEquals(parsed.topLevel.head.attributes.getString("name"), Some("hello"))
    assertEquals(parsed.topLevel.head.attributes.getInt("count"), Some(42))

  test("round-trip all primitive attribute types"):
    val op = GenericOp(
      NodeKind("test", "all"),
      AttributeMap(
        Attribute.StringAttr("s", "text"),
        Attribute.IntAttr("i", -42),
        Attribute.LongAttr("l", Long.MaxValue),
        Attribute.BoolAttr("b", true)
      )
    )
    val module = IrModule("test", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    val attrs  = parsed.topLevel.head.attributes
    assertEquals(attrs.getString("s"), Some("text"))
    assertEquals(attrs.getInt("i"), Some(-42))
    assertEquals(attrs.getLong("l"), Some(Long.MaxValue))
    assertEquals(attrs.getBool("b"), Some(true))

  test("round-trip list attribute types"):
    val op = GenericOp(
      NodeKind("test", "lists"),
      AttributeMap(
        Attribute.StringListAttr("tags", List("a", "b", "c")),
        Attribute.IntListAttr("nums", List(1, 2, 3))
      )
    )
    val module = IrModule("test", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    val attrs  = parsed.topLevel.head.attributes
    assertEquals(attrs.getStringList("tags"), Some(List("a", "b", "c")))
    assertEquals(attrs.getAs[Attribute.IntListAttr]("nums").map(_.values), Some(List(1, 2, 3)))

  test("round-trip AttrListAttr"):
    val inner = List(
      Attribute.StringAttr("x", "val"),
      Attribute.IntAttr("y", 99)
    )
    val op = GenericOp(
      NodeKind("test", "nested"),
      AttributeMap(Attribute.AttrListAttr("items", inner))
    )
    val module   = IrModule("test", Vector(op))
    val parsed   = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    val attrList = parsed.topLevel.head.attributes.getAs[Attribute.AttrListAttr]("items").get
    assertEquals(attrList.values.size, 2)

  test("round-trip AttrMapAttr"):
    val map = Map(
      "host" -> Attribute.StringAttr("host", "localhost"),
      "port" -> Attribute.IntAttr("port", 8080)
    )
    val op = GenericOp(
      NodeKind("test", "config"),
      AttributeMap(Attribute.AttrMapAttr("settings", map))
    )
    val module  = IrModule("test", Vector(op))
    val parsed  = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    val attrMap = parsed.topLevel.head.attributes.getAs[Attribute.AttrMapAttr]("settings").get
    assertEquals(attrMap.values.size, 2)
    assertEquals(attrMap.values("host").asInstanceOf[Attribute.StringAttr].value, "localhost")

  test("round-trip RefAttr"):
    val op = GenericOp(
      NodeKind("test", "ref"),
      AttributeMap(Attribute.RefAttr("target", NodeId(12345)))
    )
    val module = IrModule("test", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    val ref    = parsed.topLevel.head.attributes.getAs[Attribute.RefAttr]("target").get
    assertEquals(ref.target, NodeId(12345))

  test("round-trip nested regions"):
    val leaf       = GenericOp(NodeKind("test", "leaf"), AttributeMap(Attribute.StringAttr("v", "inner")))
    val mid        = GenericOp(NodeKind("test", "mid"), regions = Vector(Region("children", Vector(leaf))))
    val root       = GenericOp(NodeKind("test", "root"), regions = Vector(Region("body", Vector(mid))))
    val module     = IrModule("test", Vector(root))
    val parsed     = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    val parsedRoot = parsed.topLevel.head
    val parsedMid  = parsedRoot.regions.head.operations.head
    val parsedLeaf = parsedMid.regions.head.operations.head
    assertEquals(parsedLeaf.attributes.getString("v"), Some("inner"))

  test("round-trip special characters in strings"):
    val op = GenericOp(
      NodeKind("test", "esc"),
      AttributeMap(Attribute.StringAttr("text", "line1\nline2\ttab \"quoted\" back\\"))
    )
    val module = IrModule("test", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    assertEquals(parsed.topLevel.head.attributes.getString("text"), Some("line1\nline2\ttab \"quoted\" back\\"))

  test("round-trip negative and min values"):
    val op = GenericOp(
      NodeKind("test", "edge"),
      AttributeMap(
        Attribute.IntAttr("min_int", Int.MinValue),
        Attribute.LongAttr("min_long", Long.MinValue)
      )
    )
    val module = IrModule("test", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    assertEquals(parsed.topLevel.head.attributes.getInt("min_int"), Some(Int.MinValue))
    assertEquals(parsed.topLevel.head.attributes.getLong("min_long"), Some(Long.MinValue))

  test("round-trip preserves contentHash"):
    val op = GenericOp(
      NodeKind("proto", "schema"),
      AttributeMap(Attribute.StringListAttr("versions", List("v1", "v2"))),
      Vector(
        Region(
          "messages",
          Vector(
            GenericOp(NodeKind("proto", "message"), AttributeMap(Attribute.StringAttr("name", "Money")))
          )
        )
      )
    )
    val module = IrModule("proto-wrapper", Vector(op))
    val parsed = IrJsonCodec.fromJson(IrJsonCodec.toJson(module))
    assertEquals(parsed.topLevel.head.contentHash, op.contentHash)
