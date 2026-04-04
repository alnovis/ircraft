# Architecture

> The ideas behind ircraft's architecture are explored in [Compiler Ideas for Code Generation](https://alnovis.io/blog/compiler-ideas-for-code-generation).

## Core Idea

ircraft is a **framework for building code generators**, not a code generator itself.

```
A -> X -> B
```

- **A** -- source model (proto schema, OpenAPI spec, SQL DDL, any user DSL)
- **X** -- language-agnostic intermediate representation (Semantic IR)
- **B** -- target code (Java, Scala, Kotlin, TypeScript, Go, ...)

Users inject **passes** at stage X that transform the IR: add methods, generate types, resolve references, enrich metadata. ircraft provides the infrastructure; users provide the business logic.

**Analogy:** ircraft : proto-wrapper = LLVM : Clang

## Pure Functional Design

Everything is a function. Scala 3 + Cats. No mutation, no OOP inheritance.

| Concept | Type | Description |
|---------|------|-------------|
| Pass | `Kleisli[F, Module, Module]` | Composable IR transformation |
| Pipeline | `Pass andThen Pass andThen Pass` | Left-to-right Kleisli composition |
| Lowering | `Kleisli[F, Source, Module]` | User dialect -> Semantic IR |
| Emitter | `Kleisli[F, Module, Map[Path, String]]` | IR -> source files |
| Outcome | `IorT[F, NonEmptyChain[Diagnostic], A]` | Right/Both/Left for success/warnings/errors |

`F[_]` is tagless final -- users choose their effect: `Id` for tests, `IO` for production.

## Error Handling: Outcome

Unified via `IorT` (Inclusive Or) from cats:

```scala
type Outcome[F[_], A] = IorT[F, NonEmptyChain[Diagnostic], A]
```

Three states:
- `Ior.Right(a)` -- clean success
- `Ior.Both(warnings, a)` -- success with warnings (pipeline continues)
- `Ior.Left(errors)` -- error (pipeline stops)

Smart constructors: `Outcome.ok(a)`, `Outcome.warn(msg, a)`, `Outcome.fail(msg)`.

## Module Structure

```
ircraft/
  ircraft-core/          Cats-core. IR ADTs, Pass, Pipeline, Outcome, Merge
  ircraft-emit/          CodeNode tree, Renderer, LanguageSyntax, BaseEmitter
  ircraft-io/            Cats-effect. CodeWriter, IncrementalWriter (atomic writes)
  dialects/
    proto/               Proto source dialect ADT + ProtoLowering
  emitters/
    java/                JavaEmitter + JavaSyntax + JavaTypeMapping
    scala/               ScalaEmitter + ScalaSyntax + ScalaTypeMapping
  examples/              SQL dialect end-to-end example
```

## Semantic IR (language-agnostic)

The IR describes **what** is generated, not **how** it looks in a specific language.

| IR Type | Concept | Java | Scala | Rust |
|---------|---------|------|-------|------|
| `TypeDecl(Product)` | Data type | class | class/case class | struct |
| `TypeDecl(Protocol)` | Contract | interface | trait | trait |
| `TypeDecl(Abstract)` | Partial impl | abstract class | abstract class | -- |
| `TypeDecl(Sum)` | Tagged union | sealed interface | enum/sealed trait | enum |
| `EnumDecl` | Enumeration | enum | enum/sealed trait | enum |
| `Func` | Operation | method | def | fn |
| `Field` | Property | field | val/var | field |
| `Stmt.Match` | Pattern matching | if-chain | match | match |

### TypeExpr

```
Primitive: Bool, Int8..Int64, UInt8..UInt64, Float32, Float64, Char, Str, Bytes, Void, Any
Composite: Named, ListOf, MapOf, Optional, SetOf, TupleOf
Generics:  Applied, Wildcard
Resolve:   Unresolved -> Local | Imported
Advanced:  FuncType, Union, Intersection
```

### Meta (typed metadata)

```scala
val presentIn = Meta.Key[Vector[String]]("merge.presentIn")
meta.get(presentIn)   // Option[Vector[String]]
meta.set(presentIn, Vector("v1", "v2"))
```

Identity-based keys (vault-style). Type-safe, extensible.

### Doc (structured documentation)

```scala
case class Doc(
  summary: String,
  description: Option[String],
  params: Vector[(String, String)],
  returns: Option[String],
  tags: Vector[(String, String)],
)
```

Attached via `Meta`: `meta.set(Doc.key, doc)`. Rendered by `LanguageSyntax.renderDoc` -- Javadoc for Java, Scaladoc for Scala, `///` for Rust, etc.

## Two-Phase Emission

```
Semantic IR --[BaseEmitter + LanguageSyntax, F[_]]--> F[CodeNode] --[Renderer, pure]--> String
```

**Phase 1** (effectful): build CodeNode tree using `LanguageSyntax` for language-specific decisions.

**Phase 2** (pure): `Renderer.render(tree)` -- deterministic, stack-safe (via Eval), handles indentation.

### CodeNode types

| CodeNode | Concept |
|----------|---------|
| `TypeBlock(sig, sections)` | Class/trait/enum with auto blank lines |
| `Func(sig, body)` | Function. `None` body = abstract |
| `IfElse(cond, then, else)` | If-else block |
| `MatchBlock(expr, cases)` | Pattern matching |
| `ForLoop`, `WhileLoop` | Loops |
| `TryCatch` | Exception handling |
| `Comment(text)` | Single/multi-line comment |
| `Line(text)` | Raw line |

## LanguageSyntax

Trait with ~30 hooks that parameterize BaseEmitter for any target language:

```scala
trait LanguageSyntax:
  def typeSignature(...)    // "class Foo" vs "trait Foo"
  def funcSignature(...)    // "public int getX()" vs "def x: Int"
  def fieldDecl(...)        // "final int x;" vs "val x: Int"
  def enumVariant(...)      // "RED(1)," vs "case Red extends Color(1)"
  def newExpr(...)          // "new Foo(x)" vs "Foo(x)"
  def castExpr(...)         // "((Foo) x)" vs "x.asInstanceOf[Foo]"
  def ternaryExpr(...)      // "c ? t : f" vs "if c then t else f"
  def lambdaExpr(...)       // "(x) -> body" vs "x => body"
  def matchHeader(...)      // "expr match" vs if-chain fallback
  def transformMethodName() // identity (Java) vs strip "get" + camelCase (Scala)
  def renderDoc(doc)        // "/** ... */" vs "/// ..."
  def useFuncEqualsStyle    // false (Java) vs true (Scala: def x = expr)
  def supportsNativeMatch   // false (Java) vs true (Scala)
  // ...
```

Implementations: `JavaSyntax`, `ScalaSyntax(config)`.

## Multi-Language Emitters

### Java

```scala
val emitter = JavaEmitter[IO]
val files: IO[Map[Path, String]] = emitter(module)
```

### Scala 3

```scala
val emitter = ScalaEmitter.scala3[IO]
// traits, enum, def, val, =>, match, Option[T], no "get" prefix
```

### Scala 2

```scala
val emitter = ScalaEmitter.scala2[IO]
// sealed trait (no enum), new keyword, if (cond) syntax
```

### ScalaEmitterConfig

```scala
ScalaEmitterConfig(
  scalaVersion = ScalaTarget.Scala3,  // or Scala2
  enumStyle = EnumStyle.Scala3Enum,   // or SealedTrait
  useNewKeyword = false,              // Foo(x) vs new Foo(x)
)
```

## Merge System

Generic N-way merge with user-provided conflict resolution:

```scala
trait MergeStrategy[F[_]]:
  def onConflict(conflict: Conflict): Outcome[F, Resolution]

Merge.merge(versions, strategy)  // Outcome[F, Module]
```

Resolution options: `UseType`, `DualAccessor`, `Custom`, `Skip`.

## Pipeline Composition

```scala
val pipeline = Pipeline.of(
  typeResolution,
  addBuilders,
  validateResolved,
)

val result: F[Module] = Pipeline.run(pipeline, module)
```

## End-to-End Flow

```
User's ADT (ProtoFile, SqlTable, ...)
  |
  | Lowering[F, Source]
  v
Module (language-agnostic IR)
  |
  | Pass[F] >>> Pass[F] >>> ...
  v
Enriched Module
  |
  | BaseEmitter[F] (via LanguageSyntax)
  v
Map[Path, String]
  |
  | CodeWriter[F] (atomic writes)
  v
Files on disk (.java, .scala, .kt, ...)
```
