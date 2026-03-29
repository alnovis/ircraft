# Phase 8: IR Serialization & Red Tree

## Context

ircraft has a complete Green Tree (immutable, content-addressable IR nodes) and 13 nanopass
passes for proto-to-code generation. Phase 7 added per-message incremental generation.

Phase 8 adds the missing infrastructure for tooling and IDE integration:
- **Serialization** -- round-trip IR to text/JSON for debugging, testing, and data exchange
- **Red Tree** -- parent navigation and absolute positions on top of Green nodes
- **CLI** -- standalone tools for working with IR outside build systems

### Motivation: proto-wrapper IJ Idea plugin

The primary consumer of Red Tree is a future IntelliJ IDEA plugin for proto-wrapper that will:
1. Re-generate Java/Kotlin wrappers on-the-fly when editing .proto files
2. Show diagnostics with full context: "Field `amount` in `Money` (v2) conflicts with v1: int32 -> int64"
3. Navigate from generated code back to source proto definition (traceability)

The diff-tool in proto-wrapper (`SchemaDiffEngine`, `ConflictReporter`, `BreakingChangeDetector`)
currently works directly with Java proto models. Moving diff/conflict logic to IR level enables
Red Tree navigation for richer diagnostics and IDE features.

---

## Step 1: IrJsonCodec (~1 day)

**New file:** `ircraft-core/src/main/scala/io/alnovis/ircraft/core/serde/IrJsonCodec.scala`

JSON serialization for any Module/Operation. No external dependencies (same approach as
IncrementalCacheIO).

### API

```scala
object IrJsonCodec:
  /** Serialize a Module to JSON string. */
  def toJson(module: Module): String

  /** Deserialize a Module from JSON string. */
  def fromJson(json: String): Module

  /** Serialize a single Operation to JSON string. */
  def opToJson(op: Operation): String
```

### JSON Structure

```json
{
  "kind": "builtin.module",
  "name": "proto-wrapper",
  "attributes": { "key": { "type": "string", "value": "..." } },
  "regions": [
    {
      "name": "body",
      "operations": [
        {
          "kind": "proto.schema",
          "attributes": { ... },
          "regions": [ ... ]
        }
      ]
    }
  ]
}
```

### Design Decisions

- Operations are serialized as GenericOp (kind + attributes + regions) -- typed wrappers
  are reconstructed on the consumer side via pattern matching on `kind`
- Attribute types are explicit in JSON (`"type": "string"`, `"type": "int"`, etc.) to enable
  lossless round-trip
- No dependency on IrPrinter -- separate codec, different format

### Tests (~10)

- Round-trip Module with operations, attributes, regions
- Round-trip all Attribute types (String, Int, Long, Bool, StringList, IntList, AttrList, AttrMap, Ref)
- Empty module, deeply nested regions
- Proto schema end-to-end: build with DSL -> toJson -> fromJson -> compare

---

## Step 2: IrParser (~2 days)

**New file:** `ircraft-core/src/main/scala/io/alnovis/ircraft/core/serde/IrParser.scala`

Parse textual IR (produced by IrPrinter) back to Module. Recursive descent parser.

### API

```scala
object IrParser:
  /** Parse textual IR to Module. */
  def parse(text: String): Either[ParseError, Module]

  /** Parse a single operation from text. */
  def parseOp(text: String): Either[ParseError, GenericOp]

case class ParseError(message: String, line: Int, column: Int)
```

### Grammar (subset of MLIR textual format)

```
module     ::= 'module' STRING attrs? '{' operation* '}'
operation  ::= QUALIFIED_NAME attrs? ('{' region* '}')?
region     ::= IDENT '{' operation* '}'
attrs      ::= '[' attr (',' attr)* ']'
attr       ::= IDENT '=' attr_value
attr_value ::= STRING | INT | LONG | BOOL | list | map | ref
list       ::= '[' (attr_value (',' attr_value)*)? ']'
map        ::= '{' (IDENT '=' attr_value (',' IDENT '=' attr_value)*)? '}'
ref        ::= '@' INT
```

### Design Decisions

- Produces GenericOp (not typed operations) -- the parser is dialect-agnostic
- Error recovery: report first error with line/column, no partial results
- Whitespace and comments (lines starting with `//`) are skipped

### Tests (~15)

- Round-trip: IrPrinter.print(module) -> IrParser.parse -> IrPrinter.print -> compare text
- All attribute types
- Nested regions, empty regions
- Error cases: malformed input, missing closing brace, invalid attribute
- Proto schema round-trip via GenericOp (build with DSL -> lower -> print -> parse -> compare structure)

---

## Step 3: RedNode + RedTree (~2 days)

**New files:**
- `ircraft-core/src/main/scala/io/alnovis/ircraft/core/red/RedNode.scala`
- `ircraft-core/src/main/scala/io/alnovis/ircraft/core/red/RedTree.scala`

### RedNode

Wraps a green Operation with parent pointer and absolute position. Constructed lazily
(on-demand during navigation).

```scala
final class RedNode[+T <: Operation](
  val green: T,
  val parent: Option[RedNode[Operation]],
  val offset: Int,              // absolute position in the tree (sum of preceding sibling sizes)
  val childIndex: Int           // index among parent's children
):
  /** All children as RedNodes, lazily constructed. */
  lazy val children: Vector[RedNode[Operation]]

  /** Path from root to this node (list of RedNodes). */
  def ancestors: List[RedNode[Operation]]

  /** Depth in the tree (root = 0). */
  def depth: Int

  /** Navigate to next/previous sibling. */
  def nextSibling: Option[RedNode[Operation]]
  def prevSibling: Option[RedNode[Operation]]

  /** Find descendant by predicate. */
  def find(p: Operation => Boolean): Option[RedNode[Operation]]

  /** Content hash delegates to green node. */
  def contentHash: Int = green.contentHash
```

### RedTree

Top-level facade for building and querying the Red Tree.

```scala
object RedTree:
  /** Build a Red Tree from a Module. Root has no parent and offset 0. */
  def from(module: Module): RedNode[Module]

  /** Find the deepest node containing the given absolute position. */
  def locate(root: RedNode[Module], position: Int): Option[RedNode[Operation]]

  /** Collect all nodes matching a predicate with their path context. */
  def collectWithContext(
    root: RedNode[Module],
    p: Operation => Boolean
  ): Vector[RedNode[Operation]]
```

### Design Decisions

- **Lazy children**: RedNode children are created on-demand, not eagerly for the whole tree.
  This is critical for large IRs -- you only pay for the nodes you visit
- **Offset computation**: `offset` is computed from `estimatedSize` of preceding siblings.
  This is the Roslyn approach -- sizes are relative in Green, absolute in Red
- **No caching across edits**: Red Tree is thrown away on any green tree change. It's a
  view, not persistent data
- **Immutable**: RedNode is a final class (not case class to avoid copy). Navigation produces
  new RedNode instances

### Tests (~15)

- Build Red Tree from Module, verify parent pointers
- Verify absolute offsets sum correctly
- ancestors() returns path from root
- locate(position) finds correct node
- find() navigates to matching descendant
- Lazy construction: children not materialized until accessed
- Empty module, single op, deeply nested

---

## Step 4: Incremental RedTree (~1 day)

**New file:** `ircraft-core/src/main/scala/io/alnovis/ircraft/core/red/IncrementalRedTree.scala`

Reuse unchanged green subtrees when rebuilding Red Tree after an edit.

### API

```scala
object IncrementalRedTree:
  /** Rebuild Red Tree, reusing RedNodes for unchanged green subtrees. */
  def rebuild(
    oldRoot: RedNode[Module],
    newModule: Module
  ): RedNode[Module]
```

### Algorithm

1. Walk old and new green trees in parallel
2. If `oldGreen.contentHash == newGreen.contentHash` -- reuse the old RedNode subtree
   (just update parent/offset references)
3. If hashes differ -- create new RedNodes for that subtree
4. Return new root

### Design Decisions

- Hash comparison is O(1) per node -- the expensive work is already done by Green Tree
- Structural sharing: unchanged subtrees keep their lazy children intact
- This enables efficient live-edit scenarios: edit one .proto message, only the affected
  RedNode subtree is rebuilt

### Tests (~8)

- Unchanged module produces structurally equivalent Red Tree
- Single message change: only that subtree is rebuilt
- Added/removed operations handled correctly
- Reused subtrees maintain correct parent references in new tree
- Performance: large module (100 messages), change 1 -- verify O(changed) not O(total)

---

## Step 5: CLI Tools (~1 day)

**New module:** `ircraft-cli` (depends on core, all dialects)

Standalone command-line tools for working with IR.

### Commands

```
ircraft dump <file.ir>              # Parse textual IR -> pretty-print
ircraft dump --json <file.ir>       # Parse textual IR -> JSON
ircraft verify <file.ir>            # Parse + run verifier passes
ircraft lower <file.ir> --lang java # Parse proto IR -> lower -> emit
ircraft diff <a.ir> <b.ir>          # Diff two IR files (textual diff on printed form)
```

### Implementation

- Single entry point: `object IrcraftCli`
- Uses `IrParser` for input, `IrPrinter`/`IrJsonCodec` for output
- `lower` command requires dialect registration -- use a simple registry pattern
- No external CLI library -- just `args` pattern matching (keep zero-dep philosophy)

### Tests (~5)

- dump round-trip
- verify catches errors
- lower produces output files
- diff shows changes

---

## Critical Files

### New (ircraft-core):
- `core/serde/IrJsonCodec.scala` -- JSON serialization
- `core/serde/IrParser.scala` -- textual IR parser
- `core/serde/ParseError.scala` -- parse error model
- `core/red/RedNode.scala` -- Red Tree node
- `core/red/RedTree.scala` -- Red Tree facade
- `core/red/IncrementalRedTree.scala` -- incremental rebuild

### New module:
- `ircraft-cli/` -- CLI tools (sbt module)

### Modified:
- `build.sbt` -- add ircraft-cli module
- `IrPrinter.scala` -- may need minor adjustments for round-trip compatibility

### Tests:
- `core/serde/IrJsonCodecSuite.scala` (~10 tests)
- `core/serde/IrParserSuite.scala` (~15 tests)
- `core/red/RedNodeSuite.scala` (~15 tests)
- `core/red/IncrementalRedTreeSuite.scala` (~8 tests)
- `cli/IrcraftCliSuite.scala` (~5 tests)

---

## Verification

1. `sbt test` -- all existing 274+ tests pass (regression)
2. `sbt "core/test"` -- new serde + red tree tests
3. Round-trip: DSL -> Module -> IrPrinter -> IrParser -> IrPrinter -> compare
4. Round-trip: DSL -> Module -> IrJsonCodec.toJson -> fromJson -> compare
5. Red Tree: verify parent chain, offsets, locate() for proto schema IR
6. Incremental: change 1 message in 10-message schema, verify only 1 subtree rebuilt

---

## Estimate

| Component | Lines | Days |
|-----------|-------|------|
| IrJsonCodec | ~200 | 1 |
| IrParser | ~300 | 2 |
| RedNode + RedTree | ~200 | 2 |
| IncrementalRedTree | ~80 | 1 |
| CLI tools | ~150 | 1 |
| Tests | ~300 | included |
| **Total** | **~1230** | **~7** |

---

## What's Next (Phase 9+)

Phase 8 provides the foundation. Future work builds on top:

- **IJ Idea plugin** (~10+ days): IntelliJ SDK, PSI integration, live .proto editing with
  incremental regen via Red Tree. Separate project/module.
- **IR-level diff-tool**: Port SchemaDiffEngine logic to work on IR, using Red Tree for
  navigation and context in conflict reports.
- **Web IR explorer**: Visualize IR tree in browser via IrJsonCodec output.
- **Publishing** (Phase 9): Maven Central, documentation site.
