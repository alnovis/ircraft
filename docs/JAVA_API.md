# Java API Guide

ircraft provides a Java-friendly facade module (`ircraft-java-api`) that hides Scala-specific
constructs (HKT, implicits, opaque types) behind idiomatic Java interfaces.

> **Dependency** (Maven/Gradle):
> ```xml
> <dependency>
>   <groupId>io.alnovis</groupId>
>   <artifactId>ircraft-java-api_3</artifactId>
>   <version>2.0.0-alpha.2</version>
> </dependency>
> ```

## Quick Start

Full pipeline: Proto schema -> IR -> Pass -> Java source code.

```java
import io.alnovis.ircraft.java.*;
import io.alnovis.ircraft.dialects.proto.*;

// 1. Lower proto file to IR
ProtoFile proto = new ProtoFile("user.proto", ProtoSyntax.Proto3(), "user",
    Optional.of("com.example.user"), Optional.empty(), true,
    List.of(new ProtoMessage("User", List.of(
        new ProtoField("id", 1, ProtoType.Int64(), ProtoLabel.Optional(), Optional.empty()),
        new ProtoField("name", 2, ProtoType.String(), ProtoLabel.Optional(), Optional.empty())
    ), List.of(), List.of(), List.of())),
    List.of());

Result<IrModule> lowered = ProtoLoweringFacade.lower(proto);

// 2. Apply passes
IrPass addTimestamp = IrPass.pure("add-timestamp", module -> module); // your logic here
IrPass pipeline = IrPipeline.of(addTimestamp);
Result<IrModule> enriched = IrPipeline.run(pipeline, lowered.value());

// 3. Emit Java source
Result<Map<Path, String>> files = IrEmitter.java(enriched.value());
files.value().forEach((path, source) -> System.out.println(path + ":\n" + source));
```

## API Overview

| Class | Purpose |
|-------|---------|
| [`Result<T>`](#resultt) | Three-state result: ok / withWarnings / error |
| [`IrMeta`](#irmeta) | Type-safe metadata store |
| [`IrNode`](#irnode) | IR declaration node (wraps Fix[SemanticF]) |
| [`IrModule`](#irmodule) | IR module with compilation units |
| [`IrCompilationUnit`](#ircompilationunit) | Namespace + declarations |
| [`IrPass`](#irpass) | Module transformation function |
| [`IrPipeline`](#irpipeline) | Pass composition and execution |
| [`IrVisitor<T>`](#irvisitort) | Bottom-up tree visitor (replaces recursion schemes) |
| [`IrLowering<S>`](#irlowerings) | Source-to-IR conversion |
| [`ProtoLoweringFacade`](#protoloweringfacade) | Proto file -> IR lowering |
| [`IrEmitter`](#iremitter) | IR -> source code generation |
| [`IrMerge`](#irmerge) | Multi-version IR merging |

---

## Result\<T\>

Three-state result type. Every operation returns a `Result<T>`.

### States

| State | `isOk()` | `hasWarnings()` | `isError()` | `isSuccess()` | `value()` |
|-------|----------|-----------------|-------------|----------------|-----------|
| ok | true | false | false | true | returns T |
| withWarnings | false | true | false | true | returns T |
| error | false | false | true | false | throws |

### Creating Results

```java
// Success
Result<Integer> ok = Result.ok(42);

// Success with warnings
Result<Integer> warned = Result.withWarnings(42,
    List.of(new Diagnostic(Severity.Warning(), "field deprecated")));

// Error
Result<Integer> failed = Result.error("unresolved type: Foo");
Result<Integer> failed2 = Result.error(List.of(
    new Diagnostic(Severity.Error(), "type not found")));
```

### Inspecting Results

```java
Result<IrModule> result = ProtoLoweringFacade.lower(proto);

if (result.isSuccess()) {
    IrModule module = result.value();

    // Check for warnings
    for (Diagnostic w : result.warnings()) {
        log.warn(w.message());
    }
} else {
    for (Diagnostic e : result.errors()) {
        log.error(e.message());
    }
}

// Or use Optional
Optional<IrModule> opt = result.valueOpt();
```

### Chaining Operations

```java
Result<Map<Path, String>> output = ProtoLoweringFacade.lower(proto)
    .flatMap(module -> IrPipeline.run(pipeline, module))
    .flatMap(module -> IrEmitter.java(module));
```

---

## IrMeta

Type-safe metadata store. Each key is identity-unique -- two keys with the same name
but different allocations are distinct.

```java
import io.alnovis.ircraft.core.ir.Meta;

// Define keys (once, as static fields)
static final Meta.Key<String> AUTHOR_KEY = Meta.Key.apply("author");
static final Meta.Key<Integer> VERSION_KEY = Meta.Key.apply("version");

// Create and query
IrMeta meta = IrMeta.empty()
    .set(AUTHOR_KEY, "team-a")
    .set(VERSION_KEY, 3);

Optional<String> author = meta.get(AUTHOR_KEY);  // Optional.of("team-a")
boolean hasVersion = meta.contains(VERSION_KEY);  // true
IrMeta cleaned = meta.remove(AUTHOR_KEY);
```

---

## IrNode

Wraps a single IR declaration. Created via static factories.

### Declaration Kinds

| Factory | DeclKind | Represents |
|---------|----------|------------|
| `IrNode.typeDecl(...)` | `TypeDecl` | class, interface, struct |
| `IrNode.enumDecl(...)` | `EnumDecl` | enum type |
| `IrNode.funcDecl(...)` | `FuncDecl` | standalone function |
| `IrNode.aliasDecl(...)` | `AliasDecl` | type alias |
| `IrNode.constDecl(...)` | `ConstDecl` | constant value |

### Creating Nodes

```java
import io.alnovis.ircraft.core.ir.*;

// Simple class
IrNode user = IrNode.typeDecl("User", TypeKind.Product(),
    List.of(
        new Field("id", TypeExpr.Primitive.Int64()),
        new Field("name", TypeExpr.Primitive.Str())
    ));

// Enum
IrNode status = IrNode.enumDecl("Status",
    List.of(new EnumVariant("ACTIVE"), new EnumVariant("INACTIVE")));

// With nested types
IrNode outer = IrNode.typeDecl("Outer", TypeKind.Product(),
    List.of(),          // fields
    List.of(),          // functions
    List.of(user),      // nested declarations
    List.of(),          // supertypes
    List.of(),          // type params
    Visibility.Public(),
    List.of(),          // annotations
    IrMeta.empty());
```

### Accessing Node Data

```java
String name = node.name();
DeclKind kind = node.kind();
IrMeta meta = node.meta();

// Type-safe downcasting via views
if (node.asTypeDecl().isPresent()) {
    TypeDeclView view = node.asTypeDecl().get();
    List<Field> fields = view.fields();
    List<Func> methods = view.functions();
    List<IrNode> nested = view.nested();
}
```

### Views

Each declaration kind has a read-only view:

| View | Available fields |
|------|-----------------|
| `TypeDeclView` | name, kind, fields, functions, nested, supertypes, typeParams, visibility, annotations, meta |
| `EnumDeclView` | name, variants, functions, supertypes, visibility, annotations, meta |
| `FuncDeclView` | func, meta |
| `AliasDeclView` | name, target, visibility, meta |
| `ConstDeclView` | name, constType, value, visibility, meta |

---

## IrModule

Root of the IR tree. Contains compilation units, each with a namespace and declarations.

```java
IrCompilationUnit unit = IrCompilationUnit.of("com.example.model", user, status);
IrModule module = IrModule.of("my-project", unit);

// Access
String name = module.name();                     // "my-project"
List<IrCompilationUnit> units = module.units();  // [unit]
List<IrNode> allDecls = module.allDeclarations(); // flattened from all units
```

## IrCompilationUnit

A namespace (Java package) with its declarations.

```java
IrCompilationUnit unit = IrCompilationUnit.of("com.example.api",
    IrNode.typeDecl("UserService", TypeKind.Protocol()));

String ns = unit.namespace();                // "com.example.api"
List<IrNode> decls = unit.declarations();    // [UserService]
```

---

## IrPass

A function `IrModule -> Result<IrModule>`. Composable via `andThen`.

```java
// Pure pass (no errors/warnings)
IrPass rename = IrPass.pure("rename-fields", module -> {
    // transform module here
    return module;
});

// Pass that may warn or fail
IrPass validate = module -> {
    List<IrNode> decls = module.allDeclarations();
    if (decls.isEmpty()) {
        return Result.error("module has no declarations");
    }
    return Result.ok(module);
};

// Compose
IrPass combined = rename.andThen(validate);

// Identity (no-op)
IrPass noop = IrPass.identity();
```

### Error Propagation

- If a pass returns an error, subsequent passes are skipped.
- Warnings from all passes are accumulated.

## IrPipeline

Composes and runs multiple passes.

```java
IrPass pipeline = IrPipeline.of(pass1, pass2, pass3);
Result<IrModule> result = IrPipeline.run(pipeline, module);
```

---

## IrVisitor\<T\>

Bottom-up tree visitor over IR declarations. Replaces Scala recursion schemes (`scheme.cata`).

Implement the `visitXxx` methods. The `visit()` method dispatches to the correct handler
and automatically recurses into nested declarations.

```java
// Count all declarations
IrVisitor<Integer> counter = new IrVisitor<>() {
    @Override
    public Integer visitTypeDecl(TypeDeclView decl, List<Integer> nestedResults) {
        return 1 + nestedResults.stream().mapToInt(Integer::intValue).sum();
    }

    @Override
    public Integer visitEnumDecl(EnumDeclView decl) { return 1; }

    @Override
    public Integer visitFuncDecl(FuncDeclView decl) { return 1; }

    @Override
    public Integer visitAliasDecl(AliasDeclView decl) { return 1; }

    @Override
    public Integer visitConstDecl(ConstDeclView decl) { return 1; }
};

int total = counter.visit(rootNode);
```

### Collecting Names

```java
IrVisitor<List<String>> nameCollector = new IrVisitor<>() {
    @Override
    public List<String> visitTypeDecl(TypeDeclView decl, List<List<String>> nested) {
        List<String> result = new ArrayList<>();
        result.add(decl.name());
        nested.forEach(result::addAll);
        return result;
    }
    // ... other visitXxx methods return List.of(name)
};
```

---

## IrLowering\<S\>

Converts a source schema to IR. Functional interface -- implement with a lambda.

```java
IrLowering<MySchema> lowering = IrLowering.pure(schema -> {
    List<IrNode> nodes = schema.tables().stream()
        .map(t -> IrNode.typeDecl(t.name(), TypeKind.Product()))
        .collect(Collectors.toList());
    return IrModule.of("my-schema",
        IrCompilationUnit.of("com.example.model", nodes));
});

Result<IrModule> result = lowering.lower(mySchema);
```

## ProtoLoweringFacade

Built-in lowering for Protocol Buffers.

```java
Result<IrModule> result = ProtoLoweringFacade.lower(protoFile);
Result<IrModule> merged = ProtoLoweringFacade.lowerAll(List.of(file1, file2));
```

---

## IrEmitter

Generates source code from IR. Three built-in targets.

```java
Result<Map<Path, String>> javaFiles   = IrEmitter.java(module);
Result<Map<Path, String>> scala3Files = IrEmitter.scala3(module);
Result<Map<Path, String>> scala2Files = IrEmitter.scala2(module);

// Write to disk
javaFiles.value().forEach((path, source) -> {
    Files.createDirectories(path.getParent());
    Files.writeString(path, source);
});
```

---

## IrMerge

Merges multiple versions of the same schema into one IR module.

```java
Map<String, IrModule> versions = Map.of(
    "v1", moduleV1,
    "v2", moduleV2
);

// Built-in strategies
Result<IrModule> merged = IrMerge.merge(versions, IrMerge.useFirst());
Result<IrModule> merged2 = IrMerge.merge(versions, IrMerge.skip());

// Custom strategy
IrMergeStrategy custom = conflict -> {
    // conflict.declName(), conflict.memberName(), conflict.kind()
    if (conflict.kind() == ConflictKind.FieldType()) {
        return Result.ok(Resolution.UseType(TypeExpr.Primitive.Str()));
    }
    return Result.ok(Resolution.Skip());
};
Result<IrModule> merged3 = IrMerge.merge(versions, custom);
```

---

## Scala Interop

All wrapper types provide `toScala()` and `fromScala()` for interoperability:

```java
// Java -> Scala
Fix<SemanticF> scalaNode = irNode.toScala();
Module<Fix<SemanticF>> scalaModule = irModule.toScala();
Meta scalaMeta = irMeta.toScala();
Ior<Vector<Diagnostic>, T> scalaIor = result.toIor();

// Scala -> Java
IrNode javaNode = IrNode.fromScala(scalaNode);
IrModule javaModule = IrModule.fromScala(scalaModule);
IrMeta javaMeta = IrMeta.fromScala(scalaMeta);
Result<T> javaResult = Result.fromIor(scalaIor);
```

---

## Complete Example: Custom Schema -> Java Code

```java
// 1. Define source schema
record Column(String name, String sqlType) {}
record Table(String name, List<Column> columns) {}

// 2. Map SQL types to IR types
TypeExpr mapSqlType(String sqlType) {
    return switch (sqlType.toUpperCase()) {
        case "BIGINT" -> TypeExpr.Primitive.Int64();
        case "VARCHAR", "TEXT" -> TypeExpr.Primitive.Str();
        case "BOOLEAN" -> TypeExpr.Primitive.Bool();
        case "DOUBLE" -> TypeExpr.Primitive.Float64();
        default -> TypeExpr.Named.apply(sqlType);
    };
}

// 3. Create lowering
IrLowering<List<Table>> lowering = IrLowering.pure(tables -> {
    var nodes = tables.stream().map(t -> {
        var fields = t.columns().stream()
            .map(c -> new Field(c.name(), mapSqlType(c.sqlType())))
            .toList();
        return IrNode.typeDecl(t.name(), TypeKind.Product(), fields);
    }).toList();
    return IrModule.of("sql", IrCompilationUnit.of("com.example.model", nodes));
});

// 4. Define passes
IrPass addToString = IrPass.pure("add-toString", module -> module); // your logic

// 5. Run pipeline
var tables = List.of(
    new Table("User", List.of(new Column("id", "BIGINT"), new Column("name", "VARCHAR"))),
    new Table("Order", List.of(new Column("id", "BIGINT"), new Column("total", "DOUBLE")))
);

Result<Map<Path, String>> output = lowering.lower(tables)
    .flatMap(m -> IrPipeline.run(addToString, m))
    .flatMap(IrEmitter::java);

if (output.isSuccess()) {
    output.value().forEach((path, source) ->
        System.out.println("=== " + path + " ===\n" + source));
}
```
