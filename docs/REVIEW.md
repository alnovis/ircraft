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

## Recommended Fix Order

Before extending to Kotlin/Scala dialects:

1. ~~**Add traversal API**~~ — Done (2026-03-22): `Traversal.scala` with `walk()`, `collectAll()`, `size`, `walkAll()`, `transformTopLevel()`
2. **Resolve Block/Statement problem** — add `StmtVisitor` trait for uniform traversal of method bodies
3. **Extract `LanguageTypeMapping` trait** into core
4. **Clean up `width`** — rename to `estimatedSize`
5. **Fix ContentHashable consistency** — all instances via given in companions

After fixes, before 1.0.0:

6. Error recovery in lowering passes
7. Reduce Operation boilerplate (macros or base class)
8. Exhaustive matching enforcement in passes

---

## Strengths

- Clean core design with proper separation of concerns
- Good use of Scala 3 features (sealed traits, enums, opaque types, pattern matching)
- Idiomatic builder DSL
- Solid test coverage for happy paths (74 tests)
- Zero external dependencies
- Content-addressable identity infrastructure ready for incremental builds
- Well-documented (README, ARCHITECTURE, CUSTOM_DIALECT, IMPLEMENTATION_PLAN)
