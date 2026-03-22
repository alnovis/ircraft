# Creating a Custom Dialect

This guide walks through building a custom dialect from scratch — defining operations, writing a verification pass, and composing a pipeline.

## Overview

A Dialect in IRCraft is a set of Operations at a specific abstraction level. To create one:

1. Define a `Dialect` object (namespace, operation kinds)
2. Implement `Operation` case classes (your IR nodes)
3. Write a verification `Pass` (optional but recommended)
4. Write transformation passes or lowerings
5. Compose into a `Pipeline`

```mermaid
flowchart LR
    D["Define Dialect"] --> O["Define Operations"]
    O --> V["Write Verifier"]
    V --> T["Write Passes"]
    T --> P["Compose Pipeline"]

    style D fill:#4a9eff,color:#fff
    style O fill:#4a9eff,color:#fff
    style V fill:#f59e0b,color:#fff
    style T fill:#f59e0b,color:#fff
    style P fill:#10b981,color:#fff
```

## Step 1: Define the Dialect

A Dialect declares its namespace and the set of operations it owns.

```scala
package com.example.dialect

import io.alnovis.ircraft.core.*

object ConfigDialect extends Dialect:
  val namespace   = "config"
  val description = "Configuration file generation dialect"

  object Kinds:
    val File    = NodeKind(namespace, "file")
    val Section = NodeKind(namespace, "section")
    val Entry   = NodeKind(namespace, "entry")

  val operationKinds = Set(Kinds.File, Kinds.Section, Kinds.Entry)

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then List(DiagnosticMessage.error(s"${op.qualifiedName} is not a config operation"))
    else Nil
```

## Step 2: Define Operations

Operations are immutable case classes extending `Operation`. Each must provide:

| Field | Purpose |
|-------|---------|
| `kind` | Links to the Dialect via `NodeKind` |
| `attributes` | Typed metadata (`AttributeMap`) |
| `regions` | Nested operation blocks (`Vector[Region]`) |
| `span` | Optional source location |
| `contentHash` | Content-based identity (lazy) |
| `width` | Size for future Red Tree position computation |

```scala
case class ConfigFileOp(
    name: String,
    sections: Vector[ConfigSectionOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:
  val kind    = ConfigDialect.Kinds.File
  val regions = Vector(Region("sections", sections))
  lazy val contentHash = ContentHash.combine(
    ContentHash.ofString(name),
    ContentHash.ofList(sections.toList)(using Operation.operationHashable),
  )
  lazy val width = 1 + sections.map(_.width).sum


case class ConfigSectionOp(
    name: String,
    entries: Vector[ConfigEntryOp] = Vector.empty,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:
  val kind    = ConfigDialect.Kinds.Section
  val regions = Vector(Region("entries", entries))
  lazy val contentHash = ContentHash.combine(
    ContentHash.ofString(name),
    ContentHash.ofList(entries.toList)(using Operation.operationHashable),
  )
  lazy val width = 1 + entries.size


case class ConfigEntryOp(
    key: String,
    value: String,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:
  val kind    = ConfigDialect.Kinds.Entry
  val regions = Vector.empty
  lazy val contentHash = ContentHash.combine(
    ContentHash.ofString(key),
    ContentHash.ofString(value),
  )
  val width = 1
```

## Step 3: Write a Verifier

A verification pass checks structural correctness of your IR.

```scala
object ConfigVerifierPass extends Pass:
  val name        = "config-verifier"
  val description = "Validates config dialect IR"

  def run(module: Module, context: PassContext): PassResult =
    val diagnostics = module.topLevel.flatMap(verify).toList
    PassResult(module, diagnostics)

  private def verify(op: Operation): List[DiagnosticMessage] = op match
    case f: ConfigFileOp =>
      val diags = List.newBuilder[DiagnosticMessage]
      if f.name.isEmpty then
        diags += DiagnosticMessage.error("File name must not be empty", f.span)
      diags ++= f.sections.flatMap(verify)
      diags.result()

    case s: ConfigSectionOp =>
      val diags = List.newBuilder[DiagnosticMessage]
      if s.name.isEmpty then
        diags += DiagnosticMessage.error("Section name must not be empty", s.span)
      // Check for duplicate keys
      val keys = s.entries.map(_.key)
      if keys.distinct.size != keys.size then
        diags += DiagnosticMessage.error(s"Section '${s.name}' has duplicate keys", s.span)
      diags ++= s.entries.flatMap(verify)
      diags.result()

    case e: ConfigEntryOp =>
      if e.key.isEmpty then List(DiagnosticMessage.error("Entry key must not be empty", e.span))
      else Nil

    case _ => Nil
```

## Step 4: Write Passes

### Transform Pass (within dialect)

```scala
/** Sorts entries alphabetically within each section. */
object SortEntriesPass extends Pass:
  val name        = "sort-entries"
  val description = "Sorts config entries by key"

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.topLevel.map:
      case f: ConfigFileOp =>
        f.copy(sections = f.sections.map: s =>
          s.copy(entries = s.entries.sortBy(_.key)))
      case other => other
    PassResult(module.copy(topLevel = transformed))
```

### Lowering (cross-dialect)

To lower your dialect into the Semantic Dialect (for code generation):

```scala
class ConfigToSemanticLowering extends Lowering:
  val name           = "config-to-semantic"
  val description    = "Lowers config IR to semantic OOP types"
  val sourceDialect  = ConfigDialect
  val targetDialect  = SemanticDialect

  def run(module: Module, context: PassContext): PassResult =
    val files = module.topLevel.collect:
      case f: ConfigFileOp => lowerFile(f)
    PassResult(module.copy(topLevel = files))

  private def lowerFile(f: ConfigFileOp): FileOp =
    val cls = ClassOp(
      name = f.name.capitalize + "Config",
      modifiers = Set(Modifier.Public, Modifier.Final),
      fields = f.sections.flatMap(_.entries).map: e =>
        FieldDeclOp(e.key, TypeRef.STRING, Set(Modifier.Private, Modifier.Final),
          defaultValue = Some(s""""${e.value}"""")),
      // ... getters, constructor, etc.
    )
    FileOp("com.example.config", Vector(cls))
```

## Step 5: Compose a Pipeline

```scala
val pipeline = Pipeline("config-to-java",
  ConfigVerifierPass,
  SortEntriesPass,
  ConfigToSemanticLowering(),
)

// Build IR
val config = ConfigFileOp("app", Vector(
  ConfigSectionOp("database", Vector(
    ConfigEntryOp("host", "localhost"),
    ConfigEntryOp("port", "5432"),
  )),
  ConfigSectionOp("cache", Vector(
    ConfigEntryOp("ttl", "3600"),
  )),
))

// Run pipeline
val module = Module("my-config", Vector(config))
val result = pipeline.run(module, PassContext())

if result.isSuccess then
  // Emit to Java
  val emitter = DirectJavaEmitter()
  val files = emitter.emit(result.module)
  files.foreach((path, source) => println(s"$path:\n$source"))
else
  result.diagnostics.foreach(println)
```

## Step 6: Write a DSL (optional)

A builder DSL makes IR construction more ergonomic:

```scala
object ConfigDSL:
  def config(name: String)(f: FileBuilder => Unit): ConfigFileOp =
    val b = FileBuilder(name)
    f(b)
    b.build()

class FileBuilder(name: String):
  private var sections = Vector.empty[ConfigSectionOp]

  def section(name: String)(f: SectionBuilder => Unit): Unit =
    val b = SectionBuilder(name)
    f(b)
    sections = sections :+ b.build()

  def build(): ConfigFileOp = ConfigFileOp(name, sections)

class SectionBuilder(name: String):
  private var entries = Vector.empty[ConfigEntryOp]

  def entry(key: String, value: String): Unit =
    entries = entries :+ ConfigEntryOp(key, value)

  def build(): ConfigSectionOp = ConfigSectionOp(name, entries)
```

Usage:

```scala
import ConfigDSL.*

val ir = config("app") { f =>
  f.section("database") { s =>
    s.entry("host", "localhost")
    s.entry("port", "5432")
  }
}
```

## Step 7: Write a Custom Emitter (optional)

If you need a non-Java output format:

```scala
class TomlEmitter extends Emitter with EmitterUtils:
  def emit(module: Module): Map[String, String] =
    module.topLevel.collect:
      case f: ConfigFileOp => f.name + ".toml" -> emitFile(f)
    .toMap

  private def emitFile(f: ConfigFileOp): String =
    f.sections.map(emitSection).mkString("\n\n")

  private def emitSection(s: ConfigSectionOp): String =
    val header = s"[${s.name}]"
    val entries = s.entries.map(e => s"${e.key} = \"${e.value}\"")
    (header +: entries).mkString("\n")
```

## Checklist

- [ ] `Dialect` object with unique namespace
- [ ] `Operation` case classes with `kind`, `contentHash`, `regions`
- [ ] Verification pass (catches structural errors early)
- [ ] Transform passes (within dialect) and/or lowerings (cross-dialect)
- [ ] Pipeline composition
- [ ] Tests for each pass in isolation
- [ ] DSL for ergonomic IR construction (optional)
- [ ] Custom emitter for non-standard output (optional)
