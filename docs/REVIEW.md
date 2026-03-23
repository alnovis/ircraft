# Architectural Review

> Review date: 2026-03-22
> Version: 0.1.0-SNAPSHOT (Phases 0-4 complete)
> Overall assessment: **7.5/10** — solid prototype, needs architectural fixes before multi-language expansion.

## Summary

| Concern | Status | Severity | Fix Complexity |
|---------|--------|----------|----------------|
| Block/Statement/Expression outside Operation system | BROKEN | CRITICAL | High |
| No traversal API on Operation | MISSING | CRITICAL | Medium |
| Emission coupled to Semantic Dialect | LIMITING | HIGH | High |
| Custom Operation boilerplate | TEDIOUS | HIGH | Medium |
| GreenNode.width misleading semantics | MISLEADING | MEDIUM | Low |
| No LanguageTypeMapping trait | RIGID | MEDIUM | Medium |
| Region vs Block redundancy | CONFUSING | MEDIUM | High |
| ContentHashable instances fragile | FRAGILE | MEDIUM | Low |
| Pattern matching not exhaustive in passes | INCONSISTENT | MEDIUM | Low |
| DSL naming (`enum_`) | MINOR | LOW | Low |
| Error recovery in lowering | MISSING | HIGH | High |
| Incremental processing | MISSING | HIGH | High |
| IR serialization | MISSING | HIGH | High |
| Dialect plugin system | LIMITED | MEDIUM | Medium |

---

## Critical Issues

### 1. Block/Statement/Expression Break IR Uniformity

`MethodOp.body` is `Option[Block]`, but `Block`, `Statement`, and `Expression` are **not Operations**. They are separate sealed traits in the `expr/` package.

**Impact:**
- Cannot use `Module.collect()` to find all statements across methods
- Cannot write a generic statement rewriter pass
- Passes that need to transform method bodies must manually handle `Block`, `Statement`, `Expression` — a separate mini-IR inside operations
- Breaks the fundamental IR principle: all nodes should be uniformly traversable

**Affected files:**
- `dialects/semantic/src/main/scala/.../expr/Statement.scala` — `Block`, `Statement` types
- `dialects/semantic/src/main/scala/.../expr/Expression.scala` — `Expression` types
- `dialects/semantic/src/main/scala/.../ops/MethodOp.scala` — `body: Option[Block]`
- `dialects/semantic/src/main/scala/.../ops/ClassOp.scala` — `ConstructorOp.body: Option[Block]`

**Options:**
1. Make `Block`, `Statement`, `Expression` into Operations — full uniformity, but heavyweight
2. Provide a parallel visitor/traversal trait for expressions — pragmatic, keeps them lightweight
3. Hybrid: `Block` becomes a `Region`, statements become Operations, expressions stay as-is

### 2. No Traversal API

`Operation` trait provides `children` (all ops across all regions) but no:
- `walk(f: Operation => Unit)` — visit all descendants
- `transform(f: PartialFunction[Operation, Operation])` — deep rewrite
- `walkWithContext(f: (Operation, Path) => Unit)` — visit with ancestry

**Impact:** Every pass reimplements recursive traversal:

| File | Reimplements traversal |
|------|----------------------|
| `ProtoVerifierPass.scala` | `verifyOp` → recursive match + recurse into children |
| `DirectJavaEmitter.scala` | `emitTypeDecl` → recursive match for nested types |
| `DirectJavaEmitter.scala` | `collectImports` → recursive `walk()` over operations |
| `ProtoToSemanticLowering.scala` | `lowerSchema` → manual recursion over messages/enums |

**Fix:** Add to `Operation` trait or as extension methods:
```scala
extension (op: Operation)
  def walk(f: Operation => Unit): Unit
  def transform(f: PartialFunction[Operation, Operation]): Operation
  def collectAll[A](pf: PartialFunction[Operation, A]): Vector[A]
```

And add to `Module`:
```scala
def transform(f: PartialFunction[Operation, Operation]): Module
```

### 3. Emission Coupled to Semantic Dialect

`DirectJavaEmitter` directly imports and pattern-matches on semantic dialect types:

```scala
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

private def emitTypeDecl(op: Operation, level: Int): String = op match
  case i: InterfaceOp  => emitInterface(i, level)
  case c: ClassOp      => emitClass(c, level)
  case e: EnumClassOp  => emitEnum(e, level)
  case _               => s"// unsupported: ${op.qualifiedName}"
```

**Impact:**
- Cannot reuse emission logic for Kotlin or Scala
- If semantic dialect changes operation structure, Java emitter breaks
- No shared `JvmLanguageEmitter` or `CurlyBraceLanguageEmitter` base

**Affected files:**
- `dialects/java/src/main/scala/.../emit/DirectJavaEmitter.scala`

**Fix:** This is somewhat inherent to emitters (they must know what they're emitting), but could be mitigated with:
- A `LanguageTypeMapping` trait in core
- Shared formatting logic for JVM-family languages

---

## High Priority

### 4. Custom Operation Boilerplate

Every Operation case class requires:
```scala
case class MyOp(
    name: String,
    // ... domain fields
    attributes: AttributeMap = AttributeMap.empty,  // boilerplate
    span: Option[Span] = None,                      // boilerplate
) extends Operation:
  val kind = MyDialect.Kinds.MyOp                   // boilerplate
  val regions = Vector(Region("fields", fields))     // boilerplate
  lazy val contentHash = ContentHash.combine(...)    // boilerplate
  val width = 1                                      // boilerplate
```

**7 boilerplate lines per operation.** With 7 operations in proto dialect and 8 in semantic, that's ~100 lines of repeated patterns.

**Fix options:**
- Scala 3 macro annotation to derive `kind`, `contentHash`, `width`
- Abstract base class `BaseOperation` with common defaults
- Accept the boilerplate (it's explicit and readable)

### 5. GreenNode.width Misleading

Documented as "width in source representation for Red Tree position computation", but:

| Operation | width value | Actual meaning |
|-----------|-------------|----------------|
| `FieldOp` | `1` | Hardcoded constant |
| `EnumValueOp` | `1` | Hardcoded constant |
| `FieldDeclOp` | `1` | Hardcoded constant |
| `MethodOp` | `1` | Hardcoded constant |
| `SchemaOp` | sum of children | Approximate subtree size |
| `Module` | sum of topLevel | Approximate subtree size |

**Fix:** Either:
- Remove `width` from `GreenNode` until Red Tree is actually implemented (Phase 9)
- Rename to `estimatedSize` and document as non-semantic

### 6. No LanguageTypeMapping Trait

`JavaTypeMapping` is a standalone object with hardcoded mappings. No shared abstraction:

```scala
// Current: standalone, not polymorphic
object JavaTypeMapping:
  def toJavaType(ref: TypeRef): String = ...

// Better: trait in core
trait LanguageTypeMapping:
  def toLanguageType(ref: TypeRef): String
  def toBoxedType(ref: TypeRef): String
  def importsFor(ref: TypeRef): Set[String]
```

**Impact:** Kotlin and Scala dialects will copy-paste the same structure.

**Affected files:**
- `dialects/java/src/main/scala/.../types/JavaTypeMapping.scala`

---

## Medium Priority

### 7. Region vs Block Redundancy

Two concepts for "sequence of things":

| Type | Used by | Contains | Is Operation? |
|------|---------|----------|---------------|
| `Region` | All Operations | `Vector[Operation]` | No (case class) |
| `Block` | MethodOp, ConstructorOp | `List[Statement]` | No (case class) |

Both are ordered containers. `Region` is named, `Block` is not. They serve the same purpose at different IR levels.

**Fix:** Related to Issue #1. If statements become operations, `Block` becomes a `Region`.

### 8. ContentHashable Instances

`AttributeMap` has a `given ContentHashable[AttributeMap]` defined in its companion object, but it's resolved via `summon` in some places and direct `.contentHash` property in others. Inconsistent.

**Affected files:**
- `ircraft-core/src/main/scala/.../AttributeMap.scala` (line 56-57)
- `ircraft-core/src/main/scala/.../Module.scala` (line 22) — uses `summon[ContentHashable[AttributeMap]]`

**Fix:** Ensure all given instances are in companion objects and always use `summon` or direct property consistently.

### 9. Non-Exhaustive Pattern Matching in Passes

`ProtoVerifierPass.verifyOp`:
```scala
case _ => Nil  // silently ignores unknown operations
```

`DirectJavaEmitter.emitTypeDecl`:
```scala
case _ => s"// unsupported: ${op.qualifiedName}"  // silent partial implementation
```

**Fix:** At minimum, log a warning. Better: use sealed dialect operation traits with exhaustive matching where possible.

### 10. DSL Naming

`enum_()` in `ProtoSchema.scala` uses underscore to avoid Scala keyword collision.

**Alternatives:**
- `` `enum`() `` — backtick-escaped, reads better
- `enumType()` — no escaping needed, self-documenting
- `defineEnum()` — verbose but clear

---

## Not Blocking Now, Needed Later

### Error Recovery in Lowering

Pipeline stops on first error (fail-fast mode). If one message fails to lower, entire schema produces nothing.

**Needed:** Skip invalid operations, emit warnings, continue with valid parts.

### Incremental Processing

Infrastructure exists (`NodeId`, `contentHash`) but no implementation:
- No persistent cache of previous IRs
- No ability to reuse unchanged subtrees between runs
- Every invocation processes entire schema from scratch

### IR Serialization (Phase 8)

No way to persist or transmit IR:
- No JSON format
- No textual IR format (MLIR-style)
- No binary format
- Cannot run passes in separate processes

### Dialect Plugin System

Dialects are compile-time only. No runtime discovery or loading.

---

---

## Post-Refactoring Issues (2026-03-23)

> Found after two-level Operation architecture refactoring (regions as source of truth).

### P0 — Breaks content-addressability

#### ContentHash missing for body/arguments fields

`MethodOp.body`, `ConstructorOp.body`, and `EnumConstantOp.arguments` are **not included in contentHash**. Two nodes with identical signatures but different implementations produce the same hash. This will break incremental builds and caching.

| Operation | Field not hashed | Impact |
|-----------|-----------------|--------|
| `MethodOp` | `body: Option[Block]` | Abstract vs concrete method = same hash |
| `ConstructorOp` | `body: Option[Block]` | Different constructor bodies = same hash |
| `EnumConstantOp` | `arguments: List[Expression]` | `USD(0)` vs `USD(100)` = same hash |

**Fix:** Add `ContentHashable` instances for `Block`, `Statement`, `Expression`, include in hash computation.

**Affected files:**
- `dialects/semantic/src/main/scala/.../ops/MethodOp.scala`
- `dialects/semantic/src/main/scala/.../ops/ClassOp.scala` (ConstructorOp)
- `dialects/semantic/src/main/scala/.../ops/EnumClassOp.scala` (EnumConstantOp)

### P1 — Type safety / correctness

#### Unsafe asInstanceOf in ProtoToSemanticLowering

Lines 113-115: `nestedEnums.map(_.asInstanceOf[Operation])` — unnecessary cast. Scala widens `Vector[EnumClassOp]` to `Vector[Operation]` automatically. Replace with `: Vector[Operation]` type ascription or `.map(identity[Operation])`.

**Affected file:** `dialects/semantic/src/main/scala/.../lowering/ProtoToSemanticLowering.scala`

#### No validation of region names

If case class constructor is called directly with `Region("typo", ops)`, the typed lazy val accessor returns empty vector silently. Data is lost without warning.

**Mitigation:** Smart constructors (`@targetName("create")`) ensure correct region names. Consider adding a debug-mode assertion in `regionOps()`.

#### Unchecked cast in Operation.regionOps()

`@unchecked` annotation suppresses type safety warning. If a region contains wrong operation types, they are silently filtered out.

**Affected file:** `ircraft-core/src/main/scala/.../core/Operation.scala` (line 56)

### P2 — Consistency

#### nestedTypes accessor inconsistency

`ClassOp` and `InterfaceOp` use `region("nestedTypes").map(_.operations)` instead of `regionOps("nestedTypes")`. Inconsistent with how all other typed views are accessed.

**Affected files:**
- `dialects/semantic/src/main/scala/.../ops/ClassOp.scala` (line 28)
- `dialects/semantic/src/main/scala/.../ops/InterfaceOp.scala` (line 24)

#### MethodOp missing @targetName("create") smart constructor

All container operations have `@targetName("create")` companion `apply()`, but MethodOp does not. Works because it's effectively a leaf (no operation children in regions), but breaks the pattern.

**Affected file:** `dialects/semantic/src/main/scala/.../ops/MethodOp.scala`

### P3 — Documentation / polish

#### DSL `enum_()` underscore not documented

`enum` is a Scala 3 keyword, so `enum_()` is used. Not documented in code or CUSTOM_DIALECT.md.

**Affected file:** `dialects/proto/src/main/scala/.../dsl/ProtoSchema.scala` (line 46)

#### Missing test coverage

- `deepTransform` with empty regions
- `deepTransform` replacing parent with leaf of different type
- Block/Statement/Expression interaction with Operation traversal
- Multi-dialect modules (proto + semantic mixed)

---

## Recommended Fix Order

Before extending to Kotlin/Scala dialects:

1. ~~**Add traversal API**~~ — Done (2026-03-22): `Traversal.scala` with `walk()`, `collectAll()`, `size`, `walkAll()`, `deepTransform()`
2. ~~**Two-level Operation architecture**~~ — Done (2026-03-23): regions as source of truth, `mapChildren`, `Module.transform`
3. ~~**Fix contentHash for body/arguments**~~ (P0) — Done (2026-03-23): `ContentHashable` for Expression, Statement, Block, CatchClause. Included in MethodOp, ConstructorOp, EnumConstantOp hashes
4. ~~**Remove asInstanceOf in lowering**~~ (P1) — Done (2026-03-23): replaced with type ascription
5. ~~**Fix nestedTypes accessor consistency**~~ (P2) — Done (2026-03-23): ClassOp + InterfaceOp now use `regionOps`
6. ~~**Resolve Block/Statement problem**~~ — Done (2026-03-23): `ExprTraversal.scala` (Expression.walk/collectAll/transform, Statement.walkExprs/collectExprs/transformExprs, Block extensions) + `BodyTraversal.scala` (Module.collectFromBodies, Module.transformBodies bridge)
7. ~~**Extract `LanguageTypeMapping` trait**~~ — Done (2026-03-23): trait in core, JavaTypeMapping extends it, legacy aliases preserved
8. ~~**Clean up `width`**~~ — Done (2026-03-23): renamed to `estimatedSize` across 14 files
9. ~~**Fix ContentHashable consistency**~~ — Done (2026-03-23): replaced explicit given refs with summon[], Module uses attributes.contentHash directly

After fixes, before 1.0.0:

10. ~~**Error recovery in lowering**~~ — Done (2026-03-23): Try-wrapped lowering helpers, collect diagnostics, skip failed ops, continue with valid ones
11. **Reduce Operation boilerplate** — Accepted as-is. Boilerplate is explicit, readable, grep-able. 14 operations is manageable. Macros add complexity without proportional benefit.
12. ~~**Exhaustive matching in passes**~~ — Done (2026-03-23): ProtoVerifierPass warns on unknown ops, DirectJavaEmitter emits WARNING comments
13. ~~**Region name validation**~~ — Done (2026-03-23): regionOps() logs warning to stderr if region not found on non-leaf operation

---

## Strengths

- Clean two-level architecture: regions as source of truth, typed views as lazy vals
- Generic deep transforms via `mapChildren` + `Module.transform`
- Good use of Scala 3 features (sealed traits, enums, opaque types, pattern matching, `@targetName`)
- Idiomatic builder DSL with smart constructors
- Solid test coverage (96 tests)
- Zero external dependencies
- Content-addressable identity infrastructure ready for incremental builds
- Well-documented (README, ARCHITECTURE, CUSTOM_DIALECT, IMPLEMENTATION_PLAN, REVIEW)
