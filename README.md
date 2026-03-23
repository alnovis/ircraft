# IRCraft

MLIR-inspired intermediate representation library for multi-language code generation, written in Scala 3.

> Design background: [Compiler Ideas for Code Generation](https://alnovis.io/blog/compiler-ideas-for-code-generation)

## How It Works

Define your domain as a **Dialect** (set of Operations), transform through **Passes**, emit source code:

```mermaid
flowchart LR
    A["Your Dialect"] -->|Pass| B["Semantic IR"] -->|Emit| C["Source Code"]

    style A fill:#4a9eff,color:#fff
    style B fill:#f59e0b,color:#fff
    style C fill:#10b981,color:#fff
```

Each abstraction level is a Dialect. Lowerings transform between them. Passes transform within.

| Concept | Role |
|---------|------|
| **Dialect** | Groups related Operations at one abstraction level |
| **Operation** | Immutable, content-addressable IR node (GreenNode) |
| **Pass** | Transforms a Module (IR tree) — stateless, testable |
| **Pipeline** | Composes Passes — fail-fast or collect-all modes |
| **Lowering** | A Pass that crosses dialect boundaries |
| **Emitter** | Converts IR to source code (Java, Kotlin, Scala, ...) |

### Built-in Dialects

IRCraft ships with dialects for protobuf-to-code generation, but the framework is generic — build your own.

| Level | Dialect | Operations |
|-------|---------|------------|
| **High** | Proto | SchemaOp, MessageOp, FieldOp, EnumOp, OneofOp |
| **Mid** | Semantic | ClassOp, InterfaceOp, MethodOp, Expression AST |
| **Low** | Java | DirectJavaEmitter, JavaTypeMapping |

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `ircraft-core` | `ircraft-core_3` | GreenNode, Operation, Dialect, Pass, Pipeline, TypeRef, EmitterUtils |
| `ircraft-dialect-semantic` | `ircraft-dialect-semantic_3` | Language-agnostic OOP: ClassOp, InterfaceOp, MethodOp, Expression AST |
| `ircraft-dialect-proto` | `ircraft-dialect-proto_3` | Proto schema IR, Proto→Semantic lowering |
| `ircraft-dialect-java` | `ircraft-dialect-java_3` | Java emitter, type mapping |
| `ircraft-pipeline-proto-to-java` | `ircraft-pipeline-proto-to-java_3` | End-to-end pipeline: Proto → Semantic → Java |

## Quick Start

### Build

```bash
sbt compile
sbt test
sbt publishLocal   # Ivy
sbt publishM2      # Maven (~/.m2)
```

### Usage (Scala 3)

```scala
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.proto.dsl.ProtoSchema
import io.alnovis.ircraft.dialect.java.pipeline.ProtoToJavaPipeline
import io.alnovis.ircraft.dialect.semantic.lowering.LoweringConfig

// 1. Define proto schema via DSL
val schema = ProtoSchema.build("v1", "v2") { s =>
  s.message("Money") { m =>
    m.field("amount", 1, TypeRef.LONG)
    m.field("currency", 2, TypeRef.STRING)
  }
  s.enum_("Currency") { e =>
    e.value("USD", 0)
    e.value("EUR", 1)
  }
}

// 2. Configure lowering
val config = LoweringConfig(
  apiPackage = "com.example.api",
  implPackagePattern = "com.example.%s",
)

// 3. Run pipeline
val pipeline = ProtoToJavaPipeline(config)
val module = Module("my-project", Vector(schema))
val result = pipeline.execute(module)

// 4. Get generated Java source files
result match
  case Right(files) => files.foreach((path, source) => println(s"$path:\n$source"))
  case Left(errors) => errors.foreach(println)
```

### Usage from Java

```java
// IRCraft JARs work on Java classpath (Scala 3 library required at runtime)
import io.alnovis.ircraft.core.*;
import io.alnovis.ircraft.dialect.proto.ops.*;
import io.alnovis.ircraft.dialect.java.pipeline.ProtoToJavaPipeline;
import io.alnovis.ircraft.dialect.semantic.lowering.LoweringConfig;

// Construct SchemaOp directly (no DSL needed)
SchemaOp schema = new SchemaOp(/* ... */);

// Run pipeline
ProtoToJavaPipeline pipeline = new ProtoToJavaPipeline(loweringConfig);
// ...
```

## Core Concepts

### GreenNode

All IR nodes are immutable and content-addressable. Two nodes with the same content produce the same `contentHash`. This enables:
- Change detection without full tree comparison
- Node caching and reuse between generations
- Future incremental processing

### Dialect

A Dialect groups related Operations at a specific abstraction level. IRCraft ships three built-in dialects:

| Dialect | Level | Operations |
|---------|-------|------------|
| **Proto** | High | SchemaOp, MessageOp, FieldOp, EnumOp, OneofOp |
| **Semantic** | Mid | FileOp, ClassOp, InterfaceOp, MethodOp, EnumClassOp |
| **Java** | Low | DirectJavaEmitter (source generation) |

Custom dialects can be created by implementing the `Dialect` trait.

### Pass & Pipeline

A Pass transforms a `Module` (IR tree root) into a new `Module`. Passes are composed into Pipelines:

```mermaid
flowchart LR
    P1["Validate"] --> P2["Transform"] --> P3["Lower"] --> P4["Emit"]

    style P1 fill:#4a9eff,color:#fff
    style P2 fill:#4a9eff,color:#fff
    style P3 fill:#f59e0b,color:#fff
    style P4 fill:#10b981,color:#fff
```

Passes are stateless and testable in isolation. Pipelines support conditional passes, fail-fast and collect-all error modes.

### TypeRef

Language-agnostic type system with 10 variants:

```
TypeRef
├── PrimitiveType (Int32, Int64, Float32, Float64, Bool, String, Bytes, ...)
├── VoidType
├── NamedType(fqn)
├── ListType(element)
├── MapType(key, value)
├── OptionalType(inner)
├── EnumType(fqn, values)
├── UnionType(alternatives)
├── ParameterizedType(base, typeArgs)
└── WildcardType(bound)
```

### EmitterUtils

Shared formatting utilities for all language emitters. Each dialect emitter uses them independently — no inheritance, no template method:

- `indent`, `block`, `joinLines`
- `wrapComment` (JavaDoc, KDoc, ScalaDoc, LineComment)
- `commaSeparated`

## Project Structure

```
ircraft/
├── ircraft-core/                          # Core IR framework
│   └── src/main/scala/io/alnovis/ircraft/core/
│       ├── GreenNode.scala                # Immutable content-addressable node
│       ├── Operation.scala                # IR operation (extends GreenNode)
│       ├── Module.scala                   # Top-level IR container
│       ├── Region.scala                   # Nested operation blocks (MLIR)
│       ├── TypeRef.scala                  # Type system (sealed trait)
│       ├── Dialect.scala                  # Dialect trait
│       ├── Pass.scala                     # Pass, Lowering, PassResult, PassContext
│       ├── Pipeline.scala                 # Pipeline composition
│       ├── Traversal.scala                # walk, collectAll, deepTransform
│       ├── LanguageTypeMapping.scala      # Type mapping trait for emitters
│       ├── ContentHash.scala              # Hashing utilities
│       └── emit/
│           ├── Emitter.scala              # Base emitter trait
│           └── EmitterUtils.scala         # Shared formatting utilities
│
├── dialects/
│   ├── semantic/                          # Semantic Dialect (pure, no dialect deps)
│   │   └── src/main/scala/.../semantic/
│   │       ├── SemanticDialect.scala
│   │       ├── ops/                       # ClassOp, InterfaceOp, MethodOp, ...
│   │       ├── expr/                      # Expression, Statement, Block, ExprTraversal
│   │       └── BodyTraversal.scala        # Module → method body bridge
│   │
│   ├── proto/                             # Proto Dialect (depends on semantic)
│   │   └── src/main/scala/.../proto/
│   │       ├── ProtoDialect.scala
│   │       ├── ops/                       # SchemaOp, MessageOp, FieldOp, ...
│   │       ├── types/                     # ConflictType, ProtoSyntax
│   │       ├── passes/                    # ProtoVerifierPass
│   │       ├── lowering/                  # ProtoToSemanticLowering, LoweringConfig
│   │       └── dsl/                       # ProtoSchema builder DSL
│   │
│   └── java/                              # Java Code Dialect (depends on semantic)
│       └── src/main/scala/.../java/
│           ├── JavaDialect.scala
│           ├── types/                     # JavaTypeMapping
│           └── emit/                      # DirectJavaEmitter
│
├── pipelines/
│   └── proto-to-java/                     # Pipeline: Proto → Semantic → Java
│       └── src/main/scala/.../pipeline/prototojava/
│           └── ProtoToJavaPipeline.scala
│
├── docs/
│   ├── ARCHITECTURE.md                    # Detailed architecture with diagrams
│   ├── IMPLEMENTATION_PLAN.md             # Full phased roadmap
│   ├── CUSTOM_DIALECT.md                  # Guide: create your own dialect
│   └── REVIEW.md                          # Architectural review & fix tracking
│
├── build.sbt                              # Scala 3.6.4, sbt 1.10
├── .github/workflows/ci.yml              # CI: Java 17/21
└── .scalafmt.conf                         # Scala 3 formatter
```

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| 0 | Done | Project scaffold |
| 1 | Done | ircraft-core (GreenNode, TypeRef, Pass, Pipeline) |
| 2 | Done | Proto Dialect (SchemaOp, FieldOp, DSL, verifier) |
| 3 | Done | Semantic Dialect (ClassOp, Expression, Proto→Semantic lowering) |
| 4 | Done | Java Dialect (DirectJavaEmitter, end-to-end pipeline) |
| 5 | Done | proto-wrapper-plugin integration (IrcraftBridge, IrcraftGenerator) |
| 6 | Planned | Kotlin Code Dialect |
| 7 | Planned | Scala Code Dialect + SBT plugin |
| 8 | Planned | IR serialization (textual format, JSON) + CLI |
| 9 | Planned | Red Tree (LSP support, parent references) |

## Tech Stack

- **Scala 3.6.4** — case classes, sealed traits, enums, pattern matching, opaque types
- **sbt 1.10** — build tool
- **MUnit** — test framework
- **Zero external dependencies** (only Scala stdlib)

## Publishing

```bash
sbt publishM2    # Local Maven repo (~/.m2)
```

Maven coordinates:
```xml
<dependency>
    <groupId>io.alnovis.ircraft</groupId>
    <artifactId>ircraft-dialect-java_3</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## License

Apache License 2.0
