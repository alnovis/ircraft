# Concept Analysis: MLIR + Nanopass + Red-Green Trees in ircraft v2

How the original inspirations map to the current pure FP implementation.

## 1. MLIR Dialects

### Original idea
MLIR structures IR into dialects -- groups of operations at a specific abstraction level. Lowerings transform between dialects (progressive lowering). Each dialect has a namespace, operation kinds, and validation.

### v1 implementation
Direct copy of MLIR API: `Dialect` trait with `namespace`, `operationKinds`, `verify()`. `Operation` trait with `kind: NodeKind`, `regions: Vector[Region]`, `attributes: AttributeMap`. `Lowering extends Pass` with `sourceDialect`/`targetDialect`.

**Problem:** MLIR is designed for SSA-based optimizing compilers with pattern rewriting, dominance analysis, and multi-level progressive lowering. ircraft does none of that. The entire `Operation`/`Region`/`NodeKind` machinery was ceremony without substance -- `InterfaceOp` stored `methods` in a `Vector[Region]` and then immediately pulled them back out via `regionOps[MethodOp]("methods")` with unchecked casts. The "dialect" abstraction required every type to extend `Operation` and register with a `Dialect` object.

### v2 implementation
**What survived (essence):**
- Progressive lowering: `Source ADT -> Semantic IR -> CodeNode -> Text` is still a multi-level pipeline
- Dialect as abstraction level: Proto ADT, Semantic IR (Module/Decl/Func), CodeNode tree are three distinct levels
- Lowering as a function between levels: `Lowering[F, Source] = Source => Pipe[F, Module]`

**What was removed (ceremony):**
- `Dialect` trait, `NodeKind`, `Region`, `Operation` -- replaced by plain case classes (ADTs)
- `operationKinds`, `verify()` registration -- user's ADT is self-describing via Scala's type system
- `AttributeMap` with string keys -- replaced by `Meta` with phantom-typed keys

**Assessment:** The MLIR *idea* of progressive lowering through abstraction levels is fully preserved. The MLIR *API surface* (operations, regions, attributes, dialects) was over-engineering for a code generation framework and is correctly removed. ircraft is not a compiler infrastructure -- it's a codegen framework. The levels exist in the type system, not in a runtime registry.

**Correspondence:**
| MLIR concept | v1 | v2 |
|-------------|----|----|
| Dialect | `Dialect` trait + registration | User's ADT (any sealed trait) |
| Operation | `Operation` trait + `NodeKind` | `Decl` / `Func` / `Expr` (sealed ADTs) |
| Region | `Region(name, ops)` | `Vector[Decl]`, `Vector[Func]` (typed fields) |
| Attribute | `AttributeMap` (string keys) | `Meta` (phantom-typed keys) |
| Lowering | `Lowering extends Pass` | `Lowering[F, Source] = Source => Pipe[F, Module]` |
| Verification | `Dialect.verify(op)` | Compile-time (sealed ADT) + `Pipe.warn`/`error` |

## 2. Nanopass Framework

### Original idea
Instead of one monolithic transformation, use many small, single-purpose passes. Each pass is a pure function: immutable input -> immutable output. Passes compose into pipelines.

### v1 implementation
`Pass` trait with `run(module: IrModule, context: PassContext): PassResult`. `Pipeline` case class with `Vector[Pass]`, `foldLeft` execution, `failFast` flag. `PassContext` is `Map[String, String]`.

**Problem:** Pass was a trait (OOP), `PassContext` was untyped `Map[String, String]`, Pipeline was an imperative `foldLeft` with manual diagnostics accumulation. Passes were "pure" in principle but the infrastructure didn't enforce or leverage this.

### v2 implementation
```
Pass[F] = Kleisli[WriterT[F, Chain[Diagnostic], *], Module, Module]
Pipeline = pass1 andThen pass2 andThen pass3
```

**What survived (essence):**
- Small, single-purpose passes -- each `Pass[F]` does one thing
- Immutable input -> immutable output -- `Module` is a case class, never mutated
- Pipeline = ordered composition -- `andThen` is Kleisli composition
- Diagnostics accumulation -- `WriterT` automatically collects from all passes
- Conditional passes -- `Pipeline.build` with enable/disable flags

**What improved:**
- Pass = function, not trait. No classes, no `name`/`description` fields, no `isEnabled`
- Composition via Kleisli `andThen` instead of `foldLeft` on a vector
- Diagnostics via WriterT instead of manual `List.newBuilder` threading
- Typed config via `F[_]` context instead of `Map[String, String]`
- `F[_]` allows passes to have effects (IO, Either, etc.) not just pure transforms

**Assessment:** The nanopass idea is the *best preserved* concept in v2. It maps naturally to functional composition. v2 is more nanopass-like than v1 because every pass is literally a function, and composition is literally function composition.

## 3. Red-Green Trees

### Original idea (Roslyn)
Split the tree into two layers:
- **Green tree:** immutable, content-addressable, no parent references, structurally shared
- **Red tree:** lightweight facade over green, adds parent refs and absolute positions, thrown away on changes

Benefits: change detection via content hash, caching, structural sharing, safe parallelism.

### v1 implementation
`GreenNode` trait with `contentHash: Int`, `estimatedSize: Int`. `RedNode` and `RedTree` classes with parent references. `IncrementalCache` using content hashes for change detection. Every `Operation` was a `GreenNode` with manually implemented `contentHash`.

**Problem:** ~3-4k lines of boilerplate `ContentHash` given instances across all types. `RedNode` was implemented but barely used. `IncrementalCache` existed but the plugin did its own caching anyway. The content hashing overhead was not justified by actual incremental behavior.

### v2 implementation
**Not directly implemented.** Here's what exists instead:

- **Immutability** -- all IR types are case classes (structurally immutable by default in Scala)
- **Content-addressable** -- not via manual `contentHash`, but via Scala's `equals`/`hashCode` on case classes (structural equality for free)
- **Structural sharing** -- free from Scala's persistent data structures. `module.copy(units = ...)` shares all unmodified `CompilationUnit` instances
- **Change detection** -- `IncrementalWriter` in `ircraft-io` uses SHA-256 of generated output, not IR hashes
- **No Red tree** -- not needed. Passes work top-down. If parent access is needed, pass the parent as function context

**Assessment:** The *benefits* of red-green trees are achieved through simpler means:

| Red-Green benefit | v1 mechanism | v2 mechanism |
|-------------------|-------------|-------------|
| Immutability | `GreenNode` trait | Scala case classes (default) |
| Structural equality | Manual `contentHash` | Scala `equals`/`hashCode` (free) |
| Structural sharing | Manual via `GreenNode` | Scala persistent collections (free) |
| Change detection | `ContentHash` per node | SHA-256 on output files |
| Parent navigation | `RedNode` wrapper | Pass parent as function argument |
| Safe parallelism | Immutable green nodes | Immutable case classes |

The red-green tree pattern solves a problem ircraft doesn't have: **incremental re-parsing of modified source text** (Roslyn's use case). ircraft doesn't parse -- it generates. The input is a schema, not editable source. There's no "user types a character, reparse the file" scenario. Content hashing at the IR node level was premature optimization.

If incremental generation becomes important (e.g., only regenerate files whose proto changed), the natural place for it is `IncrementalWriter` (SHA-256 on output), not IR-level content hashing.

## Summary

| Concept | v1 | v2 | Verdict |
|---------|----|----|---------|
| **MLIR Dialects** | Copied API surface (Dialect trait, Operation, Region, NodeKind) | Kept the idea (progressive lowering), removed the API | Essence preserved, ceremony removed |
| **Nanopass** | OOP Pass trait, imperative Pipeline foldLeft | Kleisli composition, WriterT diagnostics | Improved -- more nanopass than v1 |
| **Red-Green Trees** | Full implementation (GreenNode, RedNode, ContentHash) | Not needed -- Scala gives immutability + sharing for free | Correctly dropped -- solved a problem ircraft doesn't have |

### The real v2 inspirations

v2 is inspired less by MLIR/Roslyn and more by:
- **Cats / tagless final** -- `F[_]` on the entire path, WriterT for diagnostics, Kleisli for composition
- **Haskell IO monad / `>>=`** -- pipeline as monadic bind chain
- **Unix pipes** -- `source | transform | transform | sink`
- **LLVM's separation of concerns** -- framework (ircraft) vs product (plugin), generic IR vs domain passes
