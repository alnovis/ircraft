# Architecture (v2)

> The ideas behind ircraft's architecture -- MLIR dialects, nanopass pipelines, and compiler techniques for code generation -- are explored in [Compiler Ideas for Code Generation](https://alnovis.io/blog/compiler-ideas-for-code-generation).

## Core Idea

ircraft is a **framework for building code generators**, not a code generator itself.

```
A -> X -> B
```

- **A** -- source model (proto schema, OpenAPI spec, SQL DDL, any user DSL)
- **X** -- language-agnostic intermediate representation (Semantic IR)
- **B** -- target code (Java, Kotlin, Scala, TypeScript, Go, ...)

At stage X, users inject **effects** (passes) that transform the IR: add methods, generate types, resolve references, enrich metadata. ircraft provides the infrastructure; users provide the business logic.

**Analogy:** ircraft : proto-wrapper = LLVM : Clang

## Pure Functional Design

Everything is a function. No mutation, no OOP inheritance hierarchies, no untyped maps.

| Concept | Type | Description |
|---------|------|-------------|
| Effect stack | `Pipe[F, A] = WriterT[F, Chain[Diagnostic], A]` | Accumulates diagnostics in any monad F |
| Pass | `Kleisli[Pipe[F, *], Module, Module]` | Composable IR transformation |
| Pipeline | `Pass andThen Pass andThen Pass` | Left-to-right Kleisli composition |
| Lowering | `Source => Pipe[F, Module]` | User dialect -> Semantic IR |
| Emitter | `Module => Pipe[F, Map[Path, String]]` | Two-phase: IR -> CodeNode tree -> text |

`F[_]` is tagless final -- users choose their effect: `Id` for tests, `IO` for production, `Either` for fail-fast.

## Module Dependency Graph

```
ircraft-core          -- cats-core only. ADT + combinators
  |
  +-- ircraft-emit    -- CodeNode tree, Renderer, BaseEmitter
  |     |
  |     +-- emitters/java, kotlin, scala
  |
  +-- ircraft-io      -- cats-effect. CodeWriter, IncrementalWriter
  |
  +-- dialects/proto, openapi, graphql
  |
  +-- examples
```

**Key:** core has zero IO dependencies. Effect-ful modules are opt-in.

## Semantic IR (language-agnostic)

The IR describes **what** is generated, not **how** it looks in a specific language.

| IR Type | Concept | Maps to (Java) | Maps to (Kotlin) | Maps to (Rust) |
|---------|---------|----------------|-------------------|----------------|
| `TypeDecl(Product)` | Data type | class | data class | struct |
| `TypeDecl(Protocol)` | Contract | interface | interface | trait |
| `TypeDecl(Abstract)` | Partial impl | abstract class | abstract class | -- |
| `TypeDecl(Sum)` | Tagged union | sealed interface | sealed class | enum |
| `TypeDecl(Singleton)` | Single instance | final class | object | -- |
| `EnumDecl` | Enumeration | enum | enum class | enum |
| `Func` | Operation | method | fun | fn |
| `Field` | Property | field | property | field |
| `Expr/Stmt/Body` | Code | Java syntax | Kotlin syntax | Rust syntax |

### Type system (TypeExpr)

```
Primitive: Bool, Int8..Int64, UInt8..UInt64, Float32, Float64, Char, Str, Bytes, Void, Any
Composite: Named, ListOf, MapOf, Optional, SetOf, TupleOf
Generics:  Applied, Wildcard
Resolve:   Unresolved -> Local | Imported  (resolved during pipeline)
Advanced:  FuncType, Union, Intersection
```

No `BoxedType` (Java-specific -- emitter handles boxing). No `EnumType` with values (enums are `EnumDecl`, referenced via `Named`).

### Meta (typed metadata)

```scala
opaque type Meta = Map[Meta.Key[?], Any]
case class Key[A](name: String)  // phantom type for type-safety

// Usage:
val presentIn = Meta.Key[Vector[String]]("merge.presentIn")
meta.get(presentIn)   // Option[Vector[String]] -- type-safe
meta.set(presentIn, Vector("v1", "v2"))
```

Replaces v1's `AttributeMap` (untyped `Map[String, Attribute]` with string keys and runtime casts).

## Two-Phase Emission

```
Semantic IR --[emitDeclTree, F[_]]--> Pipe[F, CodeNode] --[Renderer.render, pure]--> String
```

**Phase 1** (effectful): construct a CodeNode tree. Each node represents a structural code concept.

| CodeNode | Concept |
|----------|---------|
| `TypeBlock(sig, sections)` | Class/interface/enum with auto blank lines between sections |
| `Func(sig, body)` | Function/method. `None` body = abstract |
| `IfElse(cond, then, else)` | Structured if-else, `} else {` rendered correctly |
| `ForLoop(header, body)` | For loop |
| `TryCatch(try, catches, finally)` | Exception handling |
| `Line(text)` | Single line of code |
| `File(header, imports, body)` | Source file with package + imports |

**Phase 2** (pure): `Renderer.render(tree, terminator)` -- deterministic, total, cannot fail. Handles indentation automatically.

**Testing** works at both levels:
- **Structural:** pattern match on CodeNode tree (no string fragility)
- **Text:** assert on rendered output (for golden tests)

## Merge System

Generic N-way merge with user-provided conflict resolution:

```scala
trait MergeStrategy[F[_]]:
  def onConflict(conflict: Conflict): Pipe[F, Resolution]

// Resolution options:
Resolution.UseType(t)         // pick one type
Resolution.DualAccessor(map)  // generate both accessors
Resolution.Custom(decls)      // fully custom code
Resolution.Skip               // drop the conflicting member
```

ircraft detects conflicts (same function, different return types across versions). The user decides how to resolve them. No hardcoded proto-specific logic in the framework.

## Pipeline Composition

```scala
// Define passes
val typeResolution: Pass[IO] = Pass[IO]("resolve-types") { module => ... }
val addBuilders: Pass[IO] = Pass.pure[IO]("add-builders") { module => ... }

// Compose
val pipeline: Pass[IO] = Pipeline.of(typeResolution, addBuilders)

// Conditional
val pipeline: Pass[IO] = Pipeline.build(Vector(
  (typeResolution, true),
  (addBuilders, config.generateBuilders),
))

// Run
val (diagnostics, result) = Pipeline.run(pipeline, module)
// diagnostics: Chain[Diagnostic] -- accumulated from ALL passes
```

## Creating a Custom Dialect

Dialect = user's ADT. No base traits, no registration.

```scala
// 1. Define your source types (just case classes)
case class SqlTable(name: String, columns: Vector[SqlColumn])
case class SqlColumn(name: String, sqlType: String, nullable: Boolean)

// 2. Define lowering
val sqlLowering: Lowering[IO, Vector[SqlTable]] = Lowering.pure { tables =>
  Module("sql", tables.map(t => CompilationUnit("com.example", Vector(
    Decl.TypeDecl(t.name, TypeKind.Product, fields = t.columns.map(c =>
      Field(c.name, sqlTypeToIr(c.sqlType))))
  ))))
}

// 3. Define passes, compose pipeline, emit
val pipeline = Pipeline.of(addAuditFields, addGetters)
val emitter = JavaEmitter[IO]

for
  module               <- sqlLowering(tables)
  (diags, enriched)    <- Pipeline.run(pipeline, module).liftPipe
  (emitDiags, files)   <- emitter(enriched)
  _                    <- CodeWriter[IO].write(outputDir, files).liftPipe
yield ()
```

## End-to-End Flow

```
User's ADT (SqlTable, ProtoFile, ...)
  |
  | Lowering[F, Source]          -- user-defined function
  v
Module (language-agnostic IR)
  |
  | Pass[F] >>> Pass[F] >>> ...  -- Kleisli composition
  v
Enriched Module
  |
  | BaseEmitter[F]               -- two-phase: CodeNode tree -> text
  v
Map[Path, String]
  |
  | CodeWriter[F]                -- cats-effect IO
  v
Files on disk
```
