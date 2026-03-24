# Creating a Custom Dialect

IRCraft offers two ways to create dialects:

| Approach | Lines of code | Type safety | Best for |
|----------|--------------|-------------|----------|
| **Generic** (quick start) | ~6 lines | Runtime | Prototyping, scripting, simple dialects |
| **Typed** (production) | ~60+ lines per op | Compile-time | Exhaustive matching, complex IR, long-lived code |

Both approaches produce Operations that work with Traversal, Pipeline, Pass, and Emitter — no compromises.

---

## Quick Start: Generic Dialect API

Define a complete dialect in 6 lines, create operations without writing any case classes.

### Define the Dialect

```scala
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*

val ConfigDialect = GenericDialect("config"):
  leaf("entry", "key" -> StringField, "value" -> StringField, "type" -> StringField)
  leaf("comment", "text" -> StringField)
  container("section", "name" -> StringField)("entries")
  container("root")("sections")
```

- `leaf(name, fields*)` — operation with no children
- `container(name, fields*)(childSlots*)` — operation with named child regions

### Create Operations

```scala
// Leaf — just fields
val entry = ConfigDialect.create("entry",
  "key" -> "host", "value" -> "localhost", "type" -> "string"
)

// Container — fields + children
val section = ConfigDialect.createContainer("section",
  Seq("name" -> "server"),
  "entries" -> Vector(entry)
)

val root = ConfigDialect.createContainer("root",
  Seq.empty,
  "sections" -> Vector(section)
)
```

### Read Fields

```scala
entry.stringField("key")     // Some("host")
entry.intField("count")      // None (doesn't exist)
section.stringField("name")  // Some("server")
section.children("entries")  // Vector(entry)
```

### Pattern Match

```scala
import io.alnovis.ircraft.core.Traversal.*

val module = Module("test", Vector(root))
module.walkAll:
  case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
    println(s"${op.stringField("key").get} = ${op.stringField("value").get}")
  case _ => ()
```

### Transform

```scala
val transformed = root.deepTransform:
  case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
    op.copy(attributes = op.attributes + Attribute.BoolAttr("validated", true))
```

### Verify

The dialect auto-validates fields against the schema:

```scala
val incomplete = GenericOp(ConfigDialect.kind("entry"),
  AttributeMap(Attribute.StringAttr("key", "x"))
)
ConfigDialect.verify(incomplete)
// List(DiagnosticMessage(Warning, "... missing fields: value, type"))
```

### Use in Pipeline

```scala
val myPass = new Pass:
  val name = "uppercase-keys"
  val description = "Uppercases all entry keys"
  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
        val upper = op.stringField("key").map(_.toUpperCase).getOrElse("")
        op.copy(attributes = op.attributes + Attribute.StringAttr("key", upper))
    PassResult(transformed)

val pipeline = Pipeline("config-pipeline", myPass)
val result = pipeline.run(module, PassContext())
```

### Supported Field Types

| FieldType | Scala type | Create | Read |
|-----------|-----------|--------|------|
| `StringField` | `String` | `"key" -> "value"` | `op.stringField("key")` |
| `IntField` | `Int` | `"count" -> 42` | `op.intField("count")` |
| `LongField` | `Long` | `"id" -> 123L` | `op.longField("id")` |
| `BoolField` | `Boolean` | `"active" -> true` | `op.boolField("active")` |
| `StringListField` | `List[String]` | `"tags" -> List("a","b")` | `op.stringListField("tags")` |

For richer types, pass raw `Attribute` instances:

```scala
ConfigDialect.create("entry",
  "key" -> "host",
  "ref" -> Attribute.RefAttr("ref", someNodeId)
)
```

---

## Production: Typed Dialect

For dialects that need compile-time type safety, exhaustive pattern matching, and structured APIs — write typed Operation case classes.

### Step 1: Define the Dialect

```scala
package com.example.dialect

import io.alnovis.ircraft.core.*

object ConfigDialect extends Dialect:
  val namespace   = "config"
  val description = "Configuration file dialect"

  object Kinds:
    val Section = NodeKind(namespace, "section")
    val Entry   = NodeKind(namespace, "entry")

  val operationKinds = Set(Kinds.Section, Kinds.Entry)

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then List(DiagnosticMessage.error(s"${op.qualifiedName} is not a config operation"))
    else Nil
```

### Step 2: Define Operations

Each operation is an immutable case class extending `Operation`:

```scala
/** Leaf operation — no children. */
case class ConfigEntryOp(
  key: String,
  value: String,
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:
  val kind: NodeKind          = ConfigDialect.Kinds.Entry
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int = ContentHash.combine(
    ContentHash.ofString(key),
    ContentHash.ofString(value)
  )
  val estimatedSize: Int = 1
```

```scala
/** Container operation — has child regions. */
case class ConfigSectionOp(
  name: String,
  regions: Vector[Region],
  attributes: AttributeMap,
  span: Option[Span]
) extends Operation:
  val kind: NodeKind = ConfigDialect.Kinds.Section

  // Typed view into region
  lazy val entries: Vector[ConfigEntryOp] = regionOps("entries")

  override def mapChildren(f: Operation => Operation): ConfigSectionOp =
    copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))

  lazy val contentHash: Int = ContentHash.combine(
    ContentHash.ofString(name),
    ContentHash.ofList(entries.toList)(using Operation.operationHashable)
  )
  lazy val estimatedSize: Int = 1 + entries.size

object ConfigSectionOp:
  import scala.annotation.targetName
  @targetName("create")
  def apply(
    name: String,
    entries: Vector[ConfigEntryOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None
  ): ConfigSectionOp = new ConfigSectionOp(
    name,
    regions = Vector(Region("entries", entries)),
    attributes, span
  )
```

### Step 3: Write a Verifier

```scala
object ConfigVerifierPass extends Pass:
  val name        = "config-verifier"
  val description = "Validates config dialect IR"

  def run(module: Module, context: PassContext): PassResult =
    val diagnostics = module.topLevel.flatMap(verify).toList
    PassResult(module, diagnostics)

  private def verify(op: Operation): List[DiagnosticMessage] = op match
    case s: ConfigSectionOp =>
      val diags = List.newBuilder[DiagnosticMessage]
      if s.name.isEmpty then
        diags += DiagnosticMessage.error("Section name must not be empty", s.span)
      val keys = s.entries.map(_.key)
      if keys.distinct.size != keys.size then
        diags += DiagnosticMessage.error(s"Section '${s.name}' has duplicate keys", s.span)
      diags ++= s.entries.flatMap(verify)
      diags.result()
    case e: ConfigEntryOp =>
      if e.key.isEmpty then List(DiagnosticMessage.error("Entry key must not be empty", e.span))
      else Nil
    case other =>
      List(DiagnosticMessage.warning(s"Unverified operation type: ${other.qualifiedName}", other.span))
```

### Step 4: Write Passes

**Transform pass (within dialect):**

```scala
object SortEntriesPass extends Pass:
  val name        = "sort-entries"
  val description = "Sorts config entries by key"

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case s: ConfigSectionOp =>
        ConfigSectionOp(s.name, entries = s.entries.sortBy(_.key), s.attributes, s.span)
    PassResult(transformed)
```

**Lowering (cross-dialect):**

```scala
class ConfigToSemanticLowering extends Lowering:
  val name           = "config-to-semantic"
  val description    = "Lowers config IR to semantic OOP types"
  val sourceDialect  = ConfigDialect
  val targetDialect  = SemanticDialect

  def run(module: Module, context: PassContext): PassResult =
    val files = module.topLevel.collect:
      case s: ConfigSectionOp => lowerSection(s)
    PassResult(module.copy(topLevel = files))

  private def lowerSection(s: ConfigSectionOp): FileOp =
    val cls = ClassOp(
      name = s.name.capitalize + "Config",
      modifiers = Set(Modifier.Public, Modifier.Final),
      fields = s.entries.map: e =>
        FieldDeclOp(e.key, TypeRef.STRING, Set(Modifier.Private, Modifier.Final),
          defaultValue = Some(s""""${e.value}""""))
    )
    FileOp("com.example.config", Vector(cls))
```

### Step 5: Compose a Pipeline

```scala
val pipeline = Pipeline("config-to-java",
  ConfigVerifierPass,
  SortEntriesPass,
  ConfigToSemanticLowering(),
)

val section = ConfigSectionOp("database", Vector(
  ConfigEntryOp("host", "localhost"),
  ConfigEntryOp("port", "5432"),
))

val module = Module("my-config", Vector(section))
val result = pipeline.run(module, PassContext())
```

### Step 6: Write a Custom Emitter (optional)

For non-Java output:

```scala
class TomlEmitter extends Emitter with EmitterUtils:
  def emit(module: Module): Map[String, String] =
    module.topLevel.collect:
      case s: ConfigSectionOp => s.name + ".toml" -> emitSection(s)
    .toMap

  private def emitSection(s: ConfigSectionOp): String =
    val header = s"[${s.name}]"
    val entries = s.entries.map(e => s"${e.key} = \"${e.value}\"")
    (header +: entries).mkString("\n")
```

---

## When to Use Which

| Scenario | Approach |
|----------|----------|
| Prototyping an idea | Generic |
| Script or one-off tool | Generic |
| < 5 operations, simple fields | Generic |
| Need exhaustive match on op types | Typed |
| Complex field types (TypeRef, Modifier) | Typed |
| Production dialect with 10+ operations | Typed |
| IDE auto-complete on op fields | Typed |
| Started generic, growing complex | Migrate to typed |

You can mix both: define some operations as typed case classes, others as GenericOp. They all implement `Operation` and work together in the same Module, Pipeline, and Traversal.

---

## Checklist

- [ ] Choose approach (generic or typed)
- [ ] Define dialect (namespace, operations)
- [ ] Verification pass (catches structural errors early)
- [ ] Transform passes and/or lowerings
- [ ] Pipeline composition
- [ ] Tests for each pass in isolation
- [ ] Emitter for output format (Java, TOML, JSON, etc.)
