# Creating a Custom Dialect

IRCraft offers two ways to create dialects:

| Approach | Lines of code | Type safety | Best for |
|----------|--------------|-------------|----------|
| **Generic** (quick start) | ~6 lines | Runtime | Prototyping, scripting, simple dialects |
| **Typed** (production) | ~60+ lines per op | Compile-time | Exhaustive matching, complex IR, long-lived code |

Both approaches produce Operations that work with Traversal, Pipeline, Pass, and Emitter — no compromises.

---

## Quick Start: Generic Dialect API

Define a complete dialect in 6 lines, create and transform operations in minutes.

### Define the Dialect

```scala
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*
import io.alnovis.ircraft.core.Traversal.*

val ConfigDialect = GenericDialect("config"):
  leaf("entry", "key" -> StringField, "value" -> StringField)
  leaf("comment", "text" -> StringField)
  container("section", "name" -> StringField)("entries")
  container("root")("sections")
```

- `leaf(name, fields*)` — operation with no children
- `container(name, fields*)(childSlots*)` — operation with named child regions

### Create Operations

```scala
// Leaf
val entry = ConfigDialect("entry", "key" -> "host", "value" -> "localhost")

// Container — child regions mixed with fields, auto-detected from schema
val section = ConfigDialect("section", "name" -> "server", "entries" -> Vector(entry))

val root = ConfigDialect("root", "sections" -> Vector(section))
```

### Read Fields

```scala
entry.stringField("key")     // Some("host")
entry.intField("count")      // None (doesn't exist)
section.stringField("name")  // Some("server")
section.children("entries")  // Vector(entry)
```

### Check Operation Kind

```scala
entry.is("entry")              // true
entry.is("section")            // false
entry.isOf(ConfigDialect)      // true
```

### Modify Operations (immutable)

```scala
// Single field
val updated = entry.withField("value", "127.0.0.1")

// Multiple fields
val bulk = entry.withFields("key" -> "port", "value" -> "8080")

// Remove a field
val minimal = entry.without("type")

// Update children
val newSection = section.withRegion("entries", Vector(entry1, entry2))
```

### Pattern Match with Extractors

Create extractors for ergonomic pattern matching — no verbose kind checks:

```scala
val Entry   = ConfigDialect.extractor("entry")
val Section = ConfigDialect.extractor("section")

// Use in transforms
val module = Module("test", Vector(root))
val transformed = module.transform:
  case Entry(e)   => e.withField("processed", true)
  case Section(s) => s.withField("name", s.stringField("name").getOrElse("").toUpperCase)
```

Compare with the raw approach:
```scala
// Before: 79 chars of ceremony
case op: GenericOp if op.kind == ConfigDialect.kind("entry") =>
  op.copy(attributes = op.attributes + Attribute.StringAttr("key", "new"))

// After: 38 chars of intent
case Entry(e) => e.withField("key", "new")
```

### Create Passes in One Expression

No need to write a full `Pass` class for simple transformations:

```scala
val addDefaults = ConfigDialect.transformPass("add-defaults"):
  case e if e.is("entry") && e.stringField("value").isEmpty =>
    e.withField("value", "default")

val uppercaseKeys = ConfigDialect.transformPass("uppercase-keys"):
  case e if e.is("entry") =>
    e.withField("key", e.stringField("key").getOrElse("").toUpperCase)
```

The partial function receives `GenericOp` instances scoped to this dialect — ops from other dialects are left untouched.

### Compose a Pipeline

```scala
val pipeline = Pipeline("config-pipeline", addDefaults, uppercaseKeys)
val result = pipeline.run(module, PassContext())
```

### Verify

The dialect auto-validates fields against the schema:

```scala
val incomplete = GenericOp(ConfigDialect.kind("entry"),
  AttributeMap(Attribute.StringAttr("key", "x"))
)
ConfigDialect.verify(incomplete)
// List(DiagnosticMessage(Warning, "... missing fields: value"))
```

### Inspect Results

```scala
result.module.topLevel.flatMap(_.collectAll:
  case Entry(e) => s"${e.stringField("key").get} = ${e.stringField("value").get}"
)
// Vector("HOST = localhost", "PORT = 8080")
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
ConfigDialect("entry",
  "key" -> "host",
  "ref" -> Attribute.RefAttr("ref", someNodeId)
)
```

### Complete Example: 5-Minute Workflow

```scala
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.GenericDialect.*
import io.alnovis.ircraft.core.Traversal.*

// 1. Define dialect
val D = GenericDialect("config"):
  leaf("entry", "key" -> StringField, "value" -> StringField)
  container("section", "name" -> StringField)("entries")

// 2. Create IR
val entries = Vector(
  D("entry", "key" -> "host", "value" -> "localhost"),
  D("entry", "key" -> "port", "value" -> "8080")
)
val section = D("section", "name" -> "server", "entries" -> entries)
val module = Module("my-config", Vector(section))

// 3. Define extractors
val Entry   = D.extractor("entry")
val Section = D.extractor("section")

// 4. Transform
val pass = D.transformPass("validate"):
  case e if e.is("entry") && e.stringField("key").exists(_.isEmpty) =>
    e.withField("key", "UNKNOWN")

// 5. Run pipeline
val result = Pipeline("config", pass).run(module, PassContext())

// 6. Collect results
val kvs = result.module.topLevel.flatMap(_.collectAll:
  case Entry(e) => e.stringField("key").get -> e.stringField("value").get
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

## API Reference: GenericOp

| Method | Returns | Description |
|--------|---------|-------------|
| `is(opName)` | `Boolean` | Check operation name (ignoring dialect) |
| `isOf(dialect)` | `Boolean` | Check dialect ownership |
| `stringField(name)` | `Option[String]` | Read string field |
| `intField(name)` | `Option[Int]` | Read int field |
| `longField(name)` | `Option[Long]` | Read long field |
| `boolField(name)` | `Option[Boolean]` | Read boolean field |
| `stringListField(name)` | `Option[List[String]]` | Read string list field |
| `children(regionName)` | `Vector[Operation]` | Get child operations from region |
| `withField(name, value)` | `GenericOp` | Immutable update: add/replace field |
| `withFields(pairs*)` | `GenericOp` | Immutable update: add/replace multiple fields |
| `without(fieldName)` | `GenericOp` | Immutable update: remove field |
| `withRegion(name, ops)` | `GenericOp` | Immutable update: replace/add region |

## API Reference: GenericDialect

| Method | Returns | Description |
|--------|---------|-------------|
| `apply(opName, fields*)` | `GenericOp` | Unified creation (auto leaf/container) |
| `create(opName, fields*)` | `GenericOp` | Create leaf operation |
| `createContainer(opName, fields, children*)` | `GenericOp` | Create container operation |
| `extractor(opName)` | `OpExtractor` | Create pattern match extractor |
| `transformPass(name)(pf)` | `Pass` | Create transform pass from PartialFunction |
| `kind(opName)` | `NodeKind` | Get NodeKind for operation name |
| `schema(opName)` | `Option[OpSchema]` | Get operation schema |
| `verify(op)` | `List[DiagnosticMessage]` | Validate operation against schema |

---

## Checklist

- [ ] Choose approach (generic or typed)
- [ ] Define dialect (namespace, operations)
- [ ] Verification pass (catches structural errors early)
- [ ] Transform passes and/or lowerings
- [ ] Pipeline composition
- [ ] Tests for each pass in isolation
- [ ] Emitter for output format (Java, TOML, JSON, etc.)
