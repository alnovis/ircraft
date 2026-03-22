# Architecture

> The ideas behind IRCraft's architecture — MLIR dialects, Nanopass pipelines, and Red-Green Trees — are explored in detail in [Compiler Ideas for Code Generation](https://alnovis.io/blog/compiler-ideas-for-code-generation).

## Dialect Levels

IRCraft structures IR into dialects — groups of operations at a specific abstraction level. Instead of one giant IR, you define separate levels and *lower* between them progressively.

```mermaid
flowchart TB
    H["High-Level Dialect\n(domain-specific)"]
    S["Semantic Dialect\n(language-agnostic)"]
    JC["Java Code Dialect"]
    KC["Kotlin Code Dialect"]
    SC["Scala Code Dialect"]
    JF[".java files"]
    KF[".kt files"]
    SF[".scala files"]

    H -- "domain operations" --> H
    H -- "lowering" --> S
    S -- "classes, interfaces, methods" --> S
    S -- "lowering" --> JC
    S -- "lowering" --> KC
    S -- "lowering" --> SC
    JC --> JF
    KC --> KF
    SC --> SF

    style H fill:#4a9eff,color:#fff
    style S fill:#f59e0b,color:#fff
    style JC fill:#10b981,color:#fff
    style KC fill:#8b5cf6,color:#fff
    style SC fill:#8b5cf6,color:#fff
```

The critical piece is the **Semantic Dialect** — it captures OOP concepts without any language-specific details. This layer is shared. Adding a new target language means writing a new Code Dialect and an emission pass. The entire High-Level → Semantic pipeline stays untouched.

## Nanopass Pipeline

Instead of one monolithic transformation, IRCraft uses many small, single-purpose passes. Each is a pure function: immutable input → immutable output. Each can be tested in isolation and conditionally enabled.

```mermaid
flowchart TB
    subgraph high["High-Level Dialect"]
        direction TB
        P1["ValidatePass\ncheck constraints"]
        P2["ResolvePass\nresolve references"]
        P3["FilterPass\napply include/exclude"]
        P1 --> P2 --> P3
    end

    subgraph mid["Semantic Dialect"]
        direction TB
        S1["LowerPass\ndomain → OOP types"]
        S2["EnrichPass\nadd getters, builders"]
        S1 --> S2
    end

    subgraph low["Emission"]
        E1["EmitPass\nIR → source files"]
    end

    high --> mid --> low

    style high fill:#4a9eff22,stroke:#4a9eff
    style mid fill:#f59e0b22,stroke:#f59e0b
    style low fill:#10b98122,stroke:#10b981
```

Passes compose into a Pipeline. The pipeline handles execution order, skips disabled passes, collects diagnostics, and supports fail-fast or collect-all error modes.

## Green-Red Trees

All IR nodes are GreenNodes — the "Green" part of [Red-Green Trees](https://ericlippert.com/2012/06/08/red-green-trees/) (Roslyn). The tree is split into two layers:

```mermaid
flowchart TB
    subgraph red["Red Tree — navigation facade"]
        direction TB
        R1["RedClassOp\n+ parent ref\n+ absolute position"]
        R2["RedMethodOp"]
        R3["RedFieldOp"]
        R1 --> R2
        R1 --> R3
    end
    subgraph green["Green Tree — immutable data"]
        direction TB
        G1["ClassOp\nhash: 0x7A3F\nwidth: 42"]
        G2["MethodOp\nhash: 0xB1C2"]
        G3["FieldOp\nhash: 0xD4E5"]
        G1 --> G2
        G1 --> G3
    end

    R1 -. "wraps" .-> G1
    R2 -. "wraps" .-> G2
    R3 -. "wraps" .-> G3

    style red fill:#ef444422,stroke:#ef4444
    style green fill:#10b98122,stroke:#10b981
```

**Green tree** (implemented):
- Immutable (Scala 3 case classes)
- Content-addressable (`contentHash` derived from content, not identity)
- No parent references (top-down navigation only)
- Relative sizes (for future position computation)

**Red tree** (planned, Phase 9):
- Lightweight facade over green nodes
- Adds parent references and absolute positions
- Created lazily during traversal
- Thrown away entirely on any change

The insight: **most of the tree doesn't change between edits.** Two nodes with the same content produce the same `contentHash`. This gives:

- **Change detection.** Compare output hash to input hash. Same hash? Skip downstream passes.
- **Caching.** Store `contentHash → generated output`. Unchanged input = cache hit.
- **Structural sharing.** If a pass modifies 2 out of 20 fields, the unmodified 18 are shared between input and output trees.
- **Safe parallelism.** Immutable input means multiple passes can read concurrently.

## How They Work Together

The three concepts are complementary:

```mermaid
flowchart TB
    subgraph mlir["MLIR Dialects — structure"]
        direction TB
        D1["High-Level Dialect\ndomain-specific operations"]
        D2["Semantic Dialect\nlanguage-agnostic OOP"]
        D3["Code Dialect\nlanguage-specific emission"]
        D1 -- "lowering" --> D2 -- "lowering" --> D3
    end

    subgraph nano["Nanopass — transformations"]
        direction TB
        N1["Within-dialect passes\nvalidate, resolve, filter"]
        N2["Cross-dialect passes\nlower between abstraction levels"]
        N3["Conditional passes\nenable/disable via config"]
        N1 --> N2 --> N3
    end

    subgraph grt["Green-Red Trees — data model"]
        direction TB
        G1["Immutable case classes\nno mutation, safe sharing"]
        G2["Content hashing\nchange detection, caching"]
        G3["Structural sharing\nreuse unchanged subtrees"]
        G1 --> G2 --> G3
    end

    mlir -- "dialects define\nwhere passes operate" --> nano
    nano -- "passes transform\nimmutable green nodes" --> grt
    grt -- "green nodes are\ntyped by dialect" --> mlir

    style mlir fill:#4a9eff22,stroke:#4a9eff
    style nano fill:#f59e0b22,stroke:#f59e0b
    style grt fill:#10b98122,stroke:#10b981
```

- **Dialects** define the abstraction levels (where)
- **Passes** define the transformations (how)
- **Green nodes** define the data model (what)

Each pass is a pure function: `GreenNode(v1) → GreenNode(v2)`. Input is never modified. This means no defensive copying, safe parallelism, and the ability to keep previous IR versions around for debugging.

---

## Module Dependency Graph

```mermaid
flowchart TB
    core["ircraft-core"]
    proto["ircraft-dialect-proto"]
    semantic["ircraft-dialect-semantic"]
    java["ircraft-dialect-java"]
    kotlin["ircraft-dialect-kotlin (planned)"]
    scala["ircraft-dialect-scala (planned)"]

    proto --> core
    semantic --> core
    semantic --> proto
    java --> core
    java --> semantic
    kotlin -.-> core
    kotlin -.-> semantic
    scala -.-> core
    scala -.-> semantic

    style core fill:#4a9eff,color:#fff
    style proto fill:#f59e0b,color:#fff
    style semantic fill:#8b5cf6,color:#fff
    style java fill:#10b981,color:#fff
    style kotlin fill:#10b981,color:#fff
    style scala fill:#10b981,color:#fff
```

## Core Framework

ircraft-core provides the foundation — all other modules depend only on it.

### IR Nodes, Identity, and Types

**IR Nodes** are the tree structure. `GreenNode` is the base — immutable and content-addressable. `Operation` extends it with dialect membership (`kind`), typed metadata (`attributes`), and nested blocks (`regions`). `Module` is the root container that passes operate on.

**Identity & Metadata** support content-based equality. `ContentHash` computes deterministic hashes via MurmurHash3 mixing. `NodeId` is an opaque wrapper over the hash. `Attribute` is a sealed trait for typed key-value pairs attached to operations. `AttributeMap` is the immutable collection.

**Type System** is language-agnostic. `TypeRef` is a sealed trait with 10 variants covering primitives, collections, generics, unions, and named types. `Modifier` is an enum for access and declaration modifiers (public, abstract, final, etc.).

```mermaid
flowchart LR
    subgraph IR["IR Nodes"]
        direction TB
        GN["GreenNode\nimmutable · content hash"]
        OP["Operation\nextends GreenNode · kind · attrs · regions"]
        MOD["Module\ntop-level container"]
        REG["Region\nnamed block of Operations"]
        GN --> OP --> MOD
        OP --> REG
    end

    subgraph Identity["Identity & Metadata"]
        direction TB
        CH["ContentHash\nMurmurHash3 mixing"]
        NID["NodeId\nopaque type over Int"]
        ATTR["Attribute\nsealed trait · typed key-value"]
        AMAP["AttributeMap\nimmutable collection"]
        CH --> NID
        ATTR --> AMAP
    end

    subgraph Types["Type System"]
        direction TB
        TR["TypeRef — sealed trait"]
        PRIM["PrimitiveType\nInt32 · Int64 · Float · Bool · String · Bytes"]
        NAMED["NamedType · ListType · MapType\nOptionalType · EnumType · UnionType"]
        MOD2["Modifier\nenum: Public · Private · Abstract · Final · ..."]
        TR --> PRIM
        TR --> NAMED
    end

    IR --> Identity
    IR --> Types

    style IR fill:#4a9eff22,stroke:#4a9eff
    style Identity fill:#4a9eff22,stroke:#4a9eff
    style Types fill:#4a9eff22,stroke:#4a9eff
```

### Dialect, Pass Framework, and Emission

**Dialect & Pass Framework** is the transformation engine. A `Dialect` groups related operations and validates them. A `Pass` transforms a `Module` — stateless, pure function. A `Lowering` is a Pass that crosses dialect boundaries. `Pipeline` composes passes with execution order, conditional skipping, and diagnostics collection.

**Emission** converts IR to output. `Emitter` is a trait that takes a `Module` and returns a map of file paths to source code. `EmitterUtils` provides shared formatting utilities (indentation, block wrapping, doc comments) used by all language emitters.

```mermaid
flowchart LR
    subgraph Framework["Dialect & Pass Framework"]
        direction TB
        DI["Dialect\nnamespace · operationKinds · verify"]
        PA["Pass\nname · run(Module) → PassResult"]
        LO["Lowering\nextends Pass · sourceDialect → targetDialect"]
        PI["Pipeline\ncomposition · fail-fast · diagnostics"]
        DI --> PA --> PI
        PA --> LO
    end

    subgraph Emit["Emission"]
        direction TB
        EM["Emitter\ntrait: emit(Module) → Map[path, source]"]
        EU["EmitterUtils\nindent · block · wrapComment"]
        EM --> EU
    end

    Framework --> Emit

    style Framework fill:#f59e0b22,stroke:#f59e0b
    style Emit fill:#10b98122,stroke:#10b981
```

---

## Built-in Dialects

### Proto Dialect

High-level protobuf schema representation. Maps directly to proto-wrapper-plugin's `MergedSchema` model.

```mermaid
flowchart TB
    S["SchemaOp\nversions · versionSyntax"] --> M["MessageOp\nname · presentInVersions"]
    S --> E["EnumOp\nname · values"]
    S --> CE["ConflictEnumOp\nfor INT_ENUM conflicts"]
    M --> F["FieldOp\nname · number · type · conflictType"]
    M --> O["OneofOp\nprotoName · caseEnumName"]
    M --> NM["Nested MessageOp"]
    M --> NE["Nested EnumOp"]
    E --> EV["EnumValueOp\nname · number"]

    style S fill:#f59e0b,color:#fff
    style M fill:#f59e0b,color:#fff
    style F fill:#fbbf24,color:#000
    style E fill:#f59e0b,color:#fff
    style O fill:#fbbf24,color:#000
```

#### ConflictType

When a field changes type between proto versions, a ConflictType is assigned:

| ConflictType | Example | Resolution |
|---|---|---|
| `None` | Same type in all versions | Direct access |
| `IntEnum` | `int32` ↔ `enum` | Dual getters: int + enum helper |
| `Widening` | `int32` → `int64` | Wider type (long) |
| `FloatDouble` | `float` → `double` | Wider type (double) |
| `StringBytes` | `string` ↔ `bytes` | Manual conversion |
| `SignedUnsigned` | `int32` ↔ `uint32` | Long for safety |
| `RepeatedSingle` | singular ↔ repeated | List |
| `PrimitiveMessage` | `int32` ↔ `Money` | Dual accessors |
| `Incompatible` | Fundamentally different | Error |

#### Proto DSL

```scala
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
```

### Semantic Dialect

Language-agnostic OOP constructs. This is the shared layer — Java, Kotlin, and Scala dialects all lower from here.

```mermaid
flowchart TB
    FILE["FileOp\npackageName"] --> IF["InterfaceOp\nname · methods · nestedTypes"]
    FILE --> CLS["ClassOp\nname · superClass · fields · methods"]
    FILE --> ENUM["EnumClassOp\nname · constants · methods"]
    CLS --> FLD["FieldDeclOp\nname · type · modifiers"]
    CLS --> CTOR["ConstructorOp\nparameters · body"]
    IF --> MTH["MethodOp\nname · returnType · params · body?"]
    CLS --> MTH2["MethodOp"]
    MTH --> BODY["Block\nList[Statement]"]
    BODY --> STMT["Statement\nReturn · VarDecl · If · ForEach · ..."]
    STMT --> EXPR["Expression\nLiteral · MethodCall · FieldAccess · ..."]

    style FILE fill:#8b5cf6,color:#fff
    style IF fill:#8b5cf6,color:#fff
    style CLS fill:#8b5cf6,color:#fff
    style ENUM fill:#8b5cf6,color:#fff
    style MTH fill:#a78bfa,color:#fff
    style MTH2 fill:#a78bfa,color:#fff
```

#### Proto → Semantic Lowering

The key transformation — encodes the generation strategy:

```mermaid
flowchart LR
    MSG["MessageOp"] --> IF["InterfaceOp\n(getters)"]
    MSG --> ABS["ClassOp\n(abstract, extract methods)"]
    MSG --> IMPL["ClassOp\n(per version impl)"]
    ENUMOP["EnumOp"] --> ENUMCLS["EnumClassOp"]
    ONEOF["OneofOp"] --> CASE["EnumClassOp\n(case enum)"]

    style MSG fill:#f59e0b,color:#fff
    style IF fill:#8b5cf6,color:#fff
    style ABS fill:#8b5cf6,color:#fff
    style IMPL fill:#8b5cf6,color:#fff
    style ENUMOP fill:#f59e0b,color:#fff
    style ENUMCLS fill:#8b5cf6,color:#fff
```

| Proto | → Semantic |
|-------|-----------|
| `MessageOp` | `InterfaceOp` + `ClassOp`(abstract) + `ClassOp`(impl per version) |
| `FieldOp` | `MethodOp`(getter) + `FieldDeclOp` + `MethodOp`(extract) |
| `EnumOp` | `EnumClassOp` |
| `OneofOp` | `EnumClassOp`(case enum) + `MethodOp`(discriminator) |

### Java Dialect

Emits Java source code from Semantic IR. No external dependencies (no JavaPoet).

```mermaid
flowchart LR
    SEM["Semantic IR"] --> TM["JavaTypeMapping\nTypeRef → Java types"]
    TM --> EM["DirectJavaEmitter\nstring templates"]
    EM --> FILES[".java files"]

    style SEM fill:#8b5cf6,color:#fff
    style EM fill:#10b981,color:#fff
    style FILES fill:#10b981,color:#fff
```

#### Type Mapping

| TypeRef | Java Type | Boxed |
|---------|-----------|-------|
| `Int32` | `int` | `Integer` |
| `Int64` | `long` | `Long` |
| `Float32` | `float` | `Float` |
| `Float64` | `double` | `Double` |
| `Bool` | `boolean` | `Boolean` |
| `StringType` | `String` | `String` |
| `Bytes` | `byte[]` | `byte[]` |
| `ListType(T)` | `List<T>` | — |
| `MapType(K,V)` | `Map<K,V>` | — |

---

## End-to-End Pipeline

```mermaid
flowchart LR
    DSL["Proto DSL\nSchemaOp"]
    VER["ProtoVerifierPass\nvalidation"]
    LOW["ProtoToSemantic\nLowering"]
    SEM["Semantic IR\nFileOp · ClassOp · MethodOp"]
    EMIT["DirectJavaEmitter\nstring templates"]
    OUT[".java files"]

    DSL --> VER --> LOW --> SEM --> EMIT --> OUT

    style DSL fill:#f59e0b,color:#fff
    style VER fill:#f59e0b,color:#fff
    style LOW fill:#8b5cf6,color:#fff
    style SEM fill:#8b5cf6,color:#fff
    style EMIT fill:#10b981,color:#fff
    style OUT fill:#10b981,color:#fff
```

---

## Extending IRCraft

### Custom Dialect

```scala
object MyDialect extends Dialect:
  val namespace = "my"
  val description = "My custom dialect"

  object Kinds:
    val Widget = NodeKind(namespace, "widget")

  val operationKinds = Set(Kinds.Widget)

  def verify(op: Operation) =
    if !owns(op) then List(DiagnosticMessage.error("Not my op"))
    else Nil

case class WidgetOp(
    name: String,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:
  val kind = MyDialect.Kinds.Widget
  val regions = Vector.empty
  lazy val contentHash = ContentHash.ofString(name)
  val width = 1
```

### Custom Pass

```scala
object MyTransformPass extends Pass:
  val name = "my-transform"
  val description = "Transforms widgets"

  def run(module: Module, context: PassContext) =
    val transformed = module.topLevel.map:
      case w: WidgetOp => w.copy(name = w.name.toUpperCase)
      case other => other
    PassResult(module.copy(topLevel = transformed))
```

### Custom Pipeline

```scala
val pipeline = Pipeline("my-pipeline",
  MyTransformPass,
  ProtoVerifierPass,
  ProtoToSemanticLowering(config),
)
val result = pipeline.run(module, PassContext())
```

For a complete walkthrough, see [Creating a Custom Dialect](CUSTOM_DIALECT.md).
