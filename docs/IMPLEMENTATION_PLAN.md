# IRCraft — Implementation Plan

## Context

**Problem:** proto-wrapper-plugin (v2.3.2) generates Java code directly from MergedSchema via JavaPoet. There is no intermediate representation (IR), making it impossible to extend to other languages without duplicating generation logic.

**Solution:** IRCraft — a standalone Scala 3 library with an MLIR-inspired architecture (dialects, operations, lowerings) + Nanopass pipeline + Green-Red Trees. Enables code generation for Java, Kotlin, and Scala from a single IR.

**End goal:** A flexible library usable independently from the plugin, an SBT plugin for Scala, and a series of articles (blog + Rock the JVM).

## Key Decisions

- **Language:** Scala 3 (no cross-compilation to 2.13)
- **Build tool:** sbt
- **Repository:** `~/pet/ircraft/` (separate from proto-wrapper)
- **Group ID:** `io.alnovis.ircraft`
- **Versioning:** Independent from proto-wrapper. proto-wrapper 3.0.0 will depend on ircraft

---

## Phase 0: Project Scaffold

**Goal:** Empty sbt multi-module project with CI and publishing configuration.

**Deliverables:**
- `~/pet/ircraft/build.sbt` — Scala 3.6.x, sbt 1.10+
- Modules:
  - `ircraft-core` (no dependencies beyond Scala stdlib)
  - `ircraft-dialect-proto` (→ ircraft-core)
  - `ircraft-dialect-semantic` (→ ircraft-core)
  - `ircraft-dialect-java` (→ ircraft-core, ircraft-dialect-semantic, JavaPoet)
  - `ircraft-bom`
- `project/plugins.sbt` — sbt-sonatype, sbt-pgp
- `.scalafmt.conf`, `.github/workflows/ci.yml`, `.gitignore`

**Exit criteria:** `sbt compile` and `sbt publishLocal` pass for all modules.

---

## Phase 1: ircraft-core — Foundation

**Goal:** Immutable Green nodes, type system, dialect and pass framework. Usable standalone — anyone can create their own dialect on top of core.

### 1A: Node Identity and Attributes

```
io.alnovis.ircraft.core/
  ContentHash       — hashing utilities (MurmurHash3-like mixing)
  NodeId            — opaque type over content hash
  ContentHashable   — type class with given instances
  Attribute         — sealed trait: StringAttr, IntAttr, BoolAttr, ListAttr, RefAttr
  AttributeMap      — immutable wrapper over Map[String, Attribute]
  Span              — source location (file, line, col, length)
```

### 1B: GreenNode and Operation

```
  GreenNode         — base immutable node (contentHash, width)
  Operation         — trait extends GreenNode: name, operands, regions, attributes, dialect
  Region            — container for operation blocks (MLIR concept)
  Block             — sequence of Operations
  Module            — top-level Operation with one Region
  NodeKind          — case class(dialect: String, name: String)
```

### 1C: Type System

```
  TypeRef           — sealed trait
    ├── PrimitiveType  — enum: Int32, Int64, Float32, Float64, Bool, String, Bytes
    ├── VoidType
    ├── NamedType(fqn: String)
    ├── ListType(element: TypeRef)
    ├── MapType(key: TypeRef, value: TypeRef)
    ├── OptionalType(inner: TypeRef)
    ├── EnumType(fqn: String, values: List[EnumValue])
    └── UnionType(alternatives: List[TypeRef])
  Modifier          — enum: Public, Private, Protected, Abstract, Final, Static, Sealed, Override
```

### 1D: Dialect and Pass Framework

```
  Dialect           — trait: name, operations, verify(op)
  Pass[I, O]        — trait: name, run(module): PassResult
  Pipeline          — composition of Passes with andThen
  Lowering          — trait extends Pass: sourceDialect → targetDialect
  PassResult        — case class(module, diagnostics)
  DiagnosticMessage — case class(severity, message, span)
  Severity          — enum: Info, Warning, Error
```

**Tests:** Identity pass, pipeline composition, content hash determinism, exhaustive pattern match on TypeRef.

**Exit criteria:** `ircraft-core` compiles, tests pass, `publishLocal` publishes the JAR. A toy example (build an IR, run an identity pass) works end-to-end.

---

## Phase 2: Proto Dialect — Protobuf Semantics

**Module:** `ircraft-dialect-proto`

### 2A: Operations

```
io.alnovis.ircraft.dialect.proto/
  ProtoDialect      — object extends Dialect
  ops/
    SchemaOp        — top-level (← MergedSchema): versions, versionSyntax
    MessageOp       — message (← MergedMessage): name, presentInVersions
    FieldOp         — field (← MergedField): name, number, type, conflictType, presentInVersions
    EnumOp          — enum (← MergedEnum)
    EnumValueOp     — enum value
    OneofOp         — oneof group (← MergedOneof)
    ConflictEnumOp  — conflict enum for INT_ENUM
  types/
    ProtoSyntax     — enum: Proto2, Proto3
    ConflictType    — enum: 11 variants (None, IntEnum, Widening, StringBytes, ...)
    ConflictHandling — enum: Native, Converted, Manual, Warning, Incompatible
```

**Mapping from proto-wrapper:**

| proto-wrapper | ircraft ProtoDialect |
|---|---|
| `MergedSchema` | `SchemaOp` |
| `MergedMessage` | `MessageOp` |
| `MergedField` + `ConflictType` (11 variants) | `FieldOp` + `ConflictType` enum |
| `MergedEnum` | `EnumOp` |
| `MergedOneof` | `OneofOp` |

### 2B: Verification Pass + Builder DSL

```
  passes/
    ProtoVerifierPass — validation: field numbers > 0, unique names, versions ≠ empty
  dsl/
    ProtoSchema.build { versions("v1","v2"); message("Money") { field(...) } }
```

**Tests:** Build SchemaOp mirroring proto-wrapper test proto files, run the verifier.

**Exit criteria:** Can construct a proto-dialect IR that faithfully represents MergedSchema.

---

## Phase 3: Semantic Dialect — Language-Agnostic OOP

**Module:** `ircraft-dialect-semantic`

### 3A: Operations

```
io.alnovis.ircraft.dialect.semantic/
  SemanticDialect   — object extends Dialect
  ops/
    FileOp          — source file (packageName, children)
    InterfaceOp     — interface (name, modifiers, typeParams, methods, extends)
    ClassOp         — class (name, modifiers, superClass, implements, fields, methods, constructors)
    EnumClassOp     — enum class (name, constants, methods)
    FieldDeclOp     — field declaration (name, type, modifiers, defaultValue)
    MethodOp        — method (name, returnType, modifiers, params, body?)
    ConstructorOp   — constructor
    Parameter       — case class(name, type, modifiers)
    TypeParam       — case class(name, bounds)
```

### 3B: Expressions and Statements

```
  expr/
    Expression      — sealed trait
      Literal, Identifier, MethodCall, FieldAccess, NewInstance, Cast,
      Conditional, BinaryOp, UnaryOp, Lambda, NullLiteral, ThisRef, SuperRef
    Statement       — sealed trait
      ExpressionStmt, ReturnStmt, VarDecl, Assignment, IfStmt,
      ForEachStmt, ThrowStmt, TryCatchStmt
    BinOperator     — enum
    UnOperator      — enum
```

### 3C: Proto → Semantic Lowering

**Key architectural piece.** Encodes the generation strategy from `GenerationOrchestrator` + all generators.

```
  lowering/
    ProtoToSemanticLowering  — Lowering[ProtoDialect, SemanticDialect]
      MessageOp → InterfaceOp + ClassOp(abstract) + ClassOp(impl per version)
      FieldOp   → MethodOp(getter) + FieldDeclOp + MethodOp(extract)
      EnumOp    → EnumClassOp
      OneofOp   → EnumClassOp(case) + MethodOp(discriminator)

    ConflictResolutionPass   — handles 11 conflict types
      (replaces FieldProcessingChain + 13 handlers → pattern matching in Scala)
      Critical files:
        /proto-wrapper-core/.../generator/conflict/FieldProcessingChain.java
        /proto-wrapper-core/.../model/MergedField.java (ConflictType enum)

    LoweringConfig — apiPackage, implPackagePattern, generateBuilders, convertWKT, ...
```

**Tests:**
- Money with 2 fields, 2 versions → 1 interface + 1 abstract + 2 impl
- INT_ENUM conflict → dual accessor
- Oneof → case enum + discriminator
- Golden tests: compare lowered semantic IR against expected structure

**Exit criteria:** Full lowering Proto→Semantic. SchemaOp → set of FileOps with interfaces, classes, methods.

---

## Phase 4: Java Code Dialect — Emission

**Module:** `ircraft-dialect-java`

### 4A: Operations

```
io.alnovis.ircraft.dialect.java/
  JavaDialect       — object extends Dialect
  ops/
    JavaFileOp      — .java file (package, imports)
    JavaClassOp     — Java class (annotations, checked exceptions)
    JavaInterfaceOp — Java interface
    JavaEnumOp      — Java enum
    JavaAnnotationOp — @Override, @Deprecated, @Generated, ...
    JavaMethodOp    — method with throws clause
  types/
    JavaTypeMapping — TypeRef → Java types (Int32→int, Bytes→byte[], etc.)
```

### 4B: Semantic → Java Lowering

```
  lowering/
    SemanticToJavaLowering — Lowering[SemanticDialect, JavaDialect]
  passes/
    JavaImportResolverPass — import resolution and deduplication
    JavaAnnotationPass     — @Override, @Generated
    JavaDocPass            — Javadoc from IR metadata
```

### 4C: Emitter (DirectEmitter — no external dependencies)

```
  emit/
    JavaEmitter       — trait: emit(Module) → Map[String, String] (filename → source)
    DirectJavaEmitter — implementation via string templates (no JavaPoet dependency)
```

**Key decision:** ircraft does not depend on JavaPoet. A custom emitter provides full control over
formatting and eliminates the external dependency. JavaPoet remains in proto-wrapper 2.x only.

### 4D: End-to-End Pipeline

```
  pipeline/
    ProtoToJavaPipeline — assembled pipeline:
      ProtoVerifierPass → ProtoToSemanticLowering → ConflictResolutionPass
      → SemanticToJavaLowering → JavaImportResolverPass → JavaAnnotationPass
      → JavaDocPass → DirectJavaEmitter
```

**Tests:** Golden tests — take proto schemas from proto-wrapper-plugin, run through the pipeline, compare with proto-wrapper's current output. Must be semantically equivalent.

**Exit criteria:** Full pipeline Proto → Java source. ircraft produces correct Java code.

---

## Phase 5: Integration with proto-wrapper-plugin

**Repository:** proto-wrapper-plugin (develop branch), NOT ircraft.

### 5A: Bridge Layer

```java
// In proto-wrapper-plugin
public class IrcraftBridge {
    public Module toProtoIR(MergedSchema schema) { ... }
}
```

Converts MergedSchema → ircraft Proto Dialect IR. Existing analysis/merging stays — only generation is replaced.

### 5B: Gradual Migration

1. Add ircraft as a dependency in `pom.xml` / `build.gradle.kts`
2. Create `IrcraftGeneratorFactory` implementing `GeneratorFactory` (integration point: `/proto-wrapper-core/.../generator/factory/GeneratorFactory.java`)
3. Configuration: `generationEngine = "ircraft" | "legacy"`
4. Golden tests for both engines → identical output
5. ircraft → default, legacy → deprecated
6. **Release proto-wrapper 3.0.0**

**Exit criteria:** proto-wrapper-plugin generates Java via ircraft, all existing tests pass.

---

## Phase 6: Kotlin Code Dialect

**Module:** `ircraft-dialect-kotlin` (in ircraft)

```
  KotlinDialect, ops/ (KotlinFileOp, DataClassOp, ExtensionFunctionOp, PropertyOp, ...)
  lowering/ SemanticToKotlinLowering
  emit/ KotlinPoetEmitter
  pipeline/ ProtoToKotlinPipeline
```

**Can be developed in parallel with Phase 5** — only needs the Semantic Dialect.

---

## Phase 7: Scala Code Dialect + SBT Plugin

**Module:** `ircraft-dialect-scala` (in ircraft)

```
  ScalaDialect, ops/ (ScalaFileOp, CaseClassOp, SealedTraitOp, ObjectOp, ...)
  lowering/ SemanticToScalaLowering
  emit/ ScalaEmitter (direct, no external dependencies)
  pipeline/ ProtoToScalaPipeline
```

**SBT Plugin:** separate module `ircraft-sbt-plugin` or within proto-wrapper-plugin.

---

## Phase 8: IR Serialization and Tooling

```
ircraft-core/serde/
  IrPrinter  — human-readable textual format (similar to MLIR textual IR)
  IrParser   — parse textual IR back to Module
  IrJsonCodec — JSON serialization for tooling integration
```

CLI: `ircraft dump`, `ircraft lower`, `ircraft verify`

---

## Phase 9: Red Tree (LSP Support)

```
ircraft-core/red/
  RedNode    — wrapper over GreenNode: parent, absolute position
  RedTree    — lazy top-down construction
  Incremental — reuse of unchanged green nodes
```

---

## Parallelization Matrix

| Phase | Depends On | Can Run In Parallel With |
|-------|-----------|--------------------------|
| 0 | — | — |
| 1 | 0 | — |
| 2 | 1 | — |
| 3 | 1 + 2 | — |
| 4 | 3 | — |
| **5** | **4** | **6, 7, 8** |
| **6** | **3** | **5, 7, 8** |
| **7** | **3** | **5, 6, 8** |
| **8** | **1** | **5, 6, 7** |
| 9 | 1 | 5, 6, 7, 8 |

## Versions and Publishing

| Milestone | ircraft | proto-wrapper |
|-----------|---------|---------------|
| Phase 1 (core) | 0.1.0 | — |
| Phase 2-3 (proto + semantic) | 0.2.0 | — |
| Phase 4 (Java pipeline) | 0.3.0 | — |
| Phase 5 start | 0.3.x | 3.0.0-alpha |
| Phase 5 stable | **1.0.0** | **3.0.0** |
| Phase 6 (Kotlin) | 1.1.0 | 3.1.0 |
| Phase 7 (Scala + SBT) | 1.2.0 | — |

Publishing: sbt-sonatype → Maven Central.

## Articles (as phases complete)

1. After Phase 1: "Designing an MLIR-inspired IR library in Scala 3" — Green Trees, content hashing, type classes
2. After Phase 3C: "Progressive Lowering: from Protobuf to OOP code" — pipeline, conflicts
3. After Phase 4: "Multi-language code generation with IRCraft" — end-to-end
4. After Phase 5: "Migrating a production plugin to IR-based generation" — integration story
5. After Phase 7: "Building an SBT plugin for Scala protobuf wrappers" — for Rock the JVM

## Risks

1. **Scala 3 JAR on Java classpath (Phase 5):** Works, but adds scala-library (~5MB). Acceptable.
2. **Immutable IR performance:** Content hashing on every construction. Mitigation: lazy hashing, structural sharing.
3. **Generation faithfulness:** ircraft must produce the same Java code as proto-wrapper. Mitigation: golden tests from Phase 4D onwards.
4. **Conflict handler complexity (13 handlers):** The most complex part. Mitigation: pattern matching in Scala makes this significantly cleaner.
