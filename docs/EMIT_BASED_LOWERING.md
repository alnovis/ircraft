# Emit-Based Lowering Framework

## Problem

Lowering passes (dialect A -> dialect B) are currently imperative: mutable buffers, for loops, manual result collection. This creates two problems:

1. **Provenance loss.** When `WidgetOp` (kind=`my:widget`) becomes `ClassOp` (kind=`semantic:class`), the source NodeKind is lost. Downstream passes cannot distinguish origins without manual attribute tagging.

2. **Boilerplate.** Every lowering has the same structure: iterate top-level ops, match by type, emit results, collect diagnostics. The pass author writes the same scaffolding every time.

```scala
// Current: imperative, repetitive
def run(module: IrModule, context: PassContext): PassResult =
  val ops = mutable.ArrayBuffer[FileOp]()
  val diags = mutable.ListBuffer[DiagnosticMessage]()

  for op <- module.topLevel do
    op match
      case w: WidgetOp =>
        ops += lowerToClass(w)
        ops += lowerToInterface(w)
      case e: EntryOp =>
        ops += lowerToEnum(e)
      case other =>
        ops += other // pass through

  PassResult(IrModule("lowered", ops.toVector), diags.toList)
```

## Vision: Declarative Lowering Rules

Inspired by MLIR's ConversionPattern and Nanopass framework. The pass author declares **what to emit** -- the framework controls creation, provenance tagging, and result collection.

```scala
object MyDialectLowering extends LoweringPass:

  def rules: LoweringRules =
    rule[WidgetOp]: widget =>
      emit(lowerToInterface(widget))
      emit(lowerToClass(widget))

    rule[EntryOp]: entry =>
      emit(lowerToEnum(entry))
```

Each `emit()` call:
1. Registers the target operation with the framework
2. Automatically tags it with source provenance (`ir.sourceNodeKind = source.kind`)
3. Tracks the source-to-target mapping for debugging/tooling
4. Collects all emitted operations into the result module

Unmatched operations pass through unchanged (configurable: pass-through or error).

The lowering functions (`lowerToInterface`, `lowerToEnum`, etc.) remain pure -- they take a source op and return a target op. The framework wraps the result.

## Core API

### LoweringPass trait

```scala
trait LoweringPass extends Pass:

  /** Declare lowering rules. Each rule matches a source operation type. */
  def rules: LoweringRules

  /** Framework implementation -- do not override. */
  final def run(module: IrModule, context: PassContext): PassResult =
    val engine = LoweringEngine(rules, context)
    engine.execute(module)
```

### LoweringRules DSL

```scala
class LoweringRules:
  private val entries = Vector.newBuilder[LoweringRule[?]]

  def rule[S <: Operation: ClassTag](f: LoweringContext ?=> S => Unit): Unit =
    entries += LoweringRule[S](summon[ClassTag[S]], f)

  def build(): Vector[LoweringRule[?]] = entries.result()
```

### LoweringContext (Scala 3 context parameter)

```scala
class LoweringContext(val source: Operation):
  private val emitted = Vector.newBuilder[Operation]
  private val diagnostics = List.newBuilder[DiagnosticMessage]

  def emit(op: Operation): Unit =
    val tagged = op.withProvenance(source.kind)
    emitted += tagged

  def emit(ops: Iterable[Operation]): Unit =
    ops.foreach(emit)

  def warn(msg: String): Unit =
    diagnostics += DiagnosticMessage.warning(msg, source.span)

  def error(msg: String): Unit =
    diagnostics += DiagnosticMessage.error(msg, source.span)

  def results: Vector[Operation] = emitted.result()
```

The `LoweringContext` is passed as a Scala 3 context parameter (`given`). Inside the rule body, `emit()` is always available without explicit passing.

### LoweringEngine

```scala
class LoweringEngine(rules: Vector[LoweringRule[?]], context: PassContext):

  def execute(module: IrModule): PassResult =
    val diags = List.newBuilder[DiagnosticMessage]
    val results = Vector.newBuilder[Operation]

    for op <- module.topLevel do
      rules.find(_.matches(op)) match
        case Some(rule) =>
          val ctx = LoweringContext(op)
          given LoweringContext = ctx
          rule.apply(op)
          results ++= ctx.results
        case None =>
          results += op // pass through unchanged

    PassResult(IrModule(module.name, results.result()), diags.result())
```

## What Provenance Enables

### 1. Downstream pass decisions

After lowering, all operations are semantic (ClassOp, InterfaceOp, etc.). But passes may need to know the origin:

```scala
// A pass that adds extra methods only to classes that came from a specific source
val sourceKind = classOp.attributes.getString("ir.sourceNodeKind")
sourceKind match
  case Some("config:section") => addConfigMethods(classOp)
  case Some("api:endpoint")   => addEndpointMethods(classOp)
  case _                      => classOp // leave unchanged
```

### 2. Source-to-target mapping for tooling

```scala
// IDE plugin: click on generated Java class -> navigate to source dialect operation
val mapping: Map[Operation, Vector[Operation]] = engine.provenanceMap
```

### 3. Diagnostics with full context

```
Error: Entry 'timeout' in section 'server' has invalid type.
  Source: ConfigSectionOp("server") -> ConfigEntryOp("timeout", "not-a-number")
  Generated: ClassOp("ServerConfig").getTimeout() -> TypeRef.INT
```

### 4. N:M lowering tracking

One source operation may produce multiple target operations (1:N). The framework tracks all of them:

```scala
rule[ServiceOp]: svc =>
  emit(lowerToInterface(svc))      // ServiceOp -> InterfaceOp
  emit(lowerToAbstractClass(svc))  // ServiceOp -> ClassOp (abstract)
  for impl <- svc.implementations do
    emit(lowerToImplClass(svc, impl)) // ServiceOp -> ClassOp (per impl)
```

All emitted operations share the same `ir.sourceNodeKind = "api:service"`.

## Prerequisite: Operation.withProvenance

Requires a generic way to inject attributes into any Operation without knowing its concrete type:

```scala
// Option A: abstract method on Operation
trait Operation extends GreenNode:
  def withAttributes(f: AttributeMap => AttributeMap): Operation

// Each case class implements via copy:
case class ClassOp(..., attributes: AttributeMap, ...) extends Operation:
  def withAttributes(f: AttributeMap => AttributeMap): ClassOp =
    copy(attributes = f(attributes))
```

```scala
// Option B: extension method using mapChildren pattern
extension (op: Operation)
  def withProvenance(sourceKind: NodeKind): Operation =
    // Requires pattern match on concrete type -- less ideal but works today
```

Option A is cleaner and enables the framework generically. It requires adding `withAttributes` to all Operation subclasses (semantic ops, generic ops, any custom typed ops).

## Implementation Phases

### Phase 1: withAttributes on Operation
- Add `def withAttributes(f: AttributeMap => AttributeMap): Operation` to Operation trait
- Implement in all existing case classes via `.copy(attributes = ...)`
- GenericOp already supports this pattern

### Phase 2: LoweringPass framework in ircraft-core
- `LoweringPass`, `LoweringContext`, `LoweringEngine`, `LoweringRules`
- Provenance tagging via `emit()`
- Diagnostics collection in context

### Phase 3: Adopt in new dialects
- New Proto Dialect lowering uses `LoweringPass` from the start
- Any custom dialect lowering benefits immediately

## Compatibility

The framework is opt-in. Existing `Pass` and `Lowering` traits are unchanged. `LoweringPass` extends `Pass` -- it works in any Pipeline. Passes can be mixed: some declarative (LoweringPass), some imperative (Pass).
