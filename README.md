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

| Level | Dialect | Operations |
|-------|---------|------------|
| **Mid** | Semantic | ClassOp, InterfaceOp, MethodOp, Expression AST |
| **Low** | Java, Kotlin, Scala | Emitters, TypeMapping |

## Modules

| Module | Artifact | Description |
|--------|----------|-------------|
| `ircraft-core` | `ircraft-core_3` | IR framework + Semantic IR (Operation, Pass, Pipeline, TypeRef, ClassOp, InterfaceOp, Expression AST) |
| `ircraft-dialect-proto` | `ircraft-dialect-proto_3` | Proto schema IR + Proto-to-Semantic lowering |
| `ircraft-dialect-java` | `ircraft-dialect-java_3` | Java emitter, type mapping |
| `ircraft-dialect-kotlin` | `ircraft-dialect-kotlin_3` | Kotlin emitter, type mapping |
| `ircraft-dialect-scala` | `ircraft-dialect-scala_3` | Scala 3 emitter, type mapping |
| `ircraft-java-api` | `ircraft-java-api_3` | Java-friendly facade: Ops, Expr, Types, IR |

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
import io.alnovis.ircraft.core.GenericDialect.*
import io.alnovis.ircraft.core.FieldType.*
import io.alnovis.ircraft.core.Traversal.*

// 1. Define a dialect
val ApiDialect = GenericDialect("api"):
  leaf("endpoint", "path" -> StringField, "method" -> StringField)
  container("service", "name" -> StringField)("endpoints")

// 2. Build IR
val endpoints = Vector(
  ApiDialect("endpoint", "path" -> "/users", "method" -> "GET"),
  ApiDialect("endpoint", "path" -> "/users", "method" -> "POST"),
)
val service = ApiDialect("service", "name" -> "UserService", "endpoints" -> endpoints)
val module = Module("my-api", Vector(service))

// 3. Transform with passes
val addPrefix = ApiDialect.transformPass("add-prefix"):
  case e if e.is("endpoint") =>
    e.withField("path", "/api/v1" + e.stringField("path").getOrElse(""))

// 4. Run pipeline
val result = Pipeline("api-gen", addPrefix).run(module, PassContext())
```

### Usage from Java

```java
import io.alnovis.ircraft.java.*;

// Build IR via Java facade
var iface = Ops.iface("UserService")
    .method(Ops.method("getUser", Types.named("User")).abstractPublic().build())
    .build();

var file = Ops.file("com.example.api").type(iface).build();
var module = IR.module("my-api", java.util.List.of(file));
```

## Core Concepts

### GreenNode

All IR nodes are immutable and content-addressable. Two nodes with the same content produce the same `contentHash`. This enables:
- Change detection without full tree comparison
- Node caching and reuse between generations
- Future incremental processing

### Dialect

A Dialect groups related Operations at a specific abstraction level:

| Dialect | Level | Operations |
|---------|-------|------------|
| **Semantic** | Mid | FileOp, ClassOp, InterfaceOp, MethodOp, EnumClassOp |
| **Java/Kotlin/Scala** | Low | Emitters (source generation) |

Custom dialects can be created by implementing the `Dialect` trait, using the `GenericDialect` API, or via `derives IrcraftSchema`.

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
│   │       └── BodyTraversal.scala        # Module -> method body bridge
│   │
│   ├── java/                              # Java Code Dialect
│   ├── kotlin/                            # Kotlin Code Dialect
│   └── scala/                             # Scala Code Dialect
│
├── ircraft-java-api/                      # Java-friendly facade
│   └── src/main/scala/.../java/
│       ├── Ops.scala                      # Builders for IR operations
│       ├── Expr.scala                     # Expression/Statement factory
│       ├── Types.scala                    # TypeRef constants + factory
│       └── IR.scala                       # Module, PassResult, collection helpers
│
├── examples/                              # Example dialects and pipelines
│
├── docs/
│   ├── ARCHITECTURE.md                    # Detailed architecture with diagrams
│   ├── CUSTOM_DIALECT.md                  # Guide: create your own dialect
│   └── JAVA_FACADE_API.md                # Java API facade documentation
│
├── build.sbt                              # Scala 3.6.4, sbt 1.10
├── .github/workflows/ci.yml              # CI: Java 17/21
└── .scalafmt.conf                         # Scala 3 formatter
```

## Roadmap

| Phase | Status | Description |
|-------|--------|-------------|
| 1 | Done | ircraft-core (GreenNode, TypeRef, Pass, Pipeline) |
| 2 | Done | Semantic Dialect (ClassOp, InterfaceOp, Expression AST) |
| 3 | Done | Java, Kotlin, Scala emitters |
| 4 | Done | Java facade API (Ops, Expr, Types, IR) |
| 5 | Done | Generic Dialect API + Derived schemas |
| 6 | Next | Proto Dialect (redesign -- simple single-schema lowering) |
| 7 | Planned | IR serialization (textual format, JSON) + CLI |
| 8 | Planned | Red Tree (LSP support, parent references) |

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
