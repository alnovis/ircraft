package io.alnovis.ircraft.core

import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*

class IrPrinterSuite extends munit.FunSuite:

  val TestDialect: GenericDialect = GenericDialect("test"):
    leaf("entry", "key" -> StringField, "value" -> StringField)
    leaf("item", "count" -> IntField, "active" -> BoolField)
    container("section", "name" -> StringField)("entries")
    container("root")("sections")

  test("print leaf op — single line with attributes"):
    val entry = TestDialect.create("entry", "key" -> "host", "value" -> "localhost")
    val output = IrPrinter.print(entry)
    assert(output.contains("test.entry"), s"Output:\n$output")
    assert(output.contains("""key="host""""), s"Output:\n$output")
    assert(output.contains("""value="localhost""""), s"Output:\n$output")
    assert(!output.contains("{"), "Leaf should be single line")

  test("print container op — block with regions"):
    val entry = TestDialect.create("entry", "key" -> "x", "value" -> "y")
    val section = TestDialect.createContainer("section", Seq("name" -> "srv"), "entries" -> Vector(entry))
    val output = IrPrinter.print(section)
    assert(output.contains("test.section"), s"Output:\n$output")
    assert(output.contains("entries {"), s"Output:\n$output")
    assert(output.contains("test.entry"), s"Output:\n$output")

  test("print module — full tree"):
    val entry1 = TestDialect.create("entry", "key" -> "host", "value" -> "localhost")
    val entry2 = TestDialect.create("entry", "key" -> "port", "value" -> "8080")
    val section = TestDialect.createContainer("section", Seq("name" -> "server"), "entries" -> Vector(entry1, entry2))
    val root = TestDialect.createContainer("root", Seq.empty, "sections" -> Vector(section))
    val module = Module("my-project", Vector(root))

    val output = IrPrinter.print(module)
    assert(output.contains("""module "my-project""""), s"Output:\n$output")
    assert(output.contains("test.root"), s"Output:\n$output")
    assert(output.contains("sections {"), s"Output:\n$output")
    assert(output.contains("test.section"), s"Output:\n$output")
    assert(output.contains("entries {"), s"Output:\n$output")
    assert(output.contains("""key="host""""), s"Output:\n$output")
    assert(output.contains("""key="port""""), s"Output:\n$output")

  test("print nested regions — correct indentation"):
    val entry = TestDialect.create("entry", "key" -> "x", "value" -> "y")
    val section = TestDialect.createContainer("section", Seq("name" -> "s"), "entries" -> Vector(entry))
    val root = TestDialect.createContainer("root", Seq.empty, "sections" -> Vector(section))
    val module = Module("test", Vector(root))
    val output = IrPrinter.print(module)

    val lines = output.split("\n")
    // module at level 0
    assert(lines.exists(_.startsWith("module")))
    // root at level 1 (2 spaces)
    assert(lines.exists(l => l.startsWith("  test.root")), s"Output:\n$output")
    // sections region at level 2
    assert(lines.exists(l => l.startsWith("    sections {")), s"Output:\n$output")
    // section at level 3
    assert(lines.exists(l => l.startsWith("      test.section")), s"Output:\n$output")

  test("print int and bool attributes"):
    val item = TestDialect.create("item", "count" -> 42, "active" -> true)
    val output = IrPrinter.print(item)
    assert(output.contains("count=42"), s"Output:\n$output")
    assert(output.contains("active=true"), s"Output:\n$output")

  test("print string list attribute"):
    val op = GenericOp(
      NodeKind("test", "tagged"),
      AttributeMap(Attribute.StringListAttr("tags", List("a", "b", "c")))
    )
    val output = IrPrinter.print(op)
    assert(output.contains("""tags=["a","b","c"]"""), s"Output:\n$output")

  test("print empty module"):
    val module = Module.empty("empty")
    val output = IrPrinter.print(module)
    assert(output.contains("""module "empty""""), s"Output:\n$output")
    assert(output.contains("}"), s"Output:\n$output")
