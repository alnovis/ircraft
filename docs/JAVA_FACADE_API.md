# Java Facade API (ircraft-java-api)

## Концепция

Отдельный модуль `ircraft-java-api` предоставляет чистый Java-facing API поверх Scala case classes.
Scala API не затрагивается. Java пользователи подключают один модуль и работают без
Scala collection conversions, Option wrapping, varargs friction.

## Мотивация

Написание Pass на Java через прямые Scala API требует:
- `scala.jdk.CollectionConverters.SeqHasAsJava(...)` на каждом обращении к коллекции
- `scala.Option.empty()` с unchecked cast для typed None
- `Modifier.valueOf("Public")` вместо enum constant
- Знание internal structure Scala companion objects
- Полные конструкторы case class (10+ аргументов без default values)

Фасад скрывает всё это за чистым Java API.

## Структура модуля

```
ircraft/
  ircraft-java-api/                     -- новый sbt модуль
    src/main/scala/io/alnovis/ircraft/java/
      Ops.scala    -- builders для IR операций
      Expr.scala   -- Expression/Statement/Block factory
      Types.scala  -- TypeRef constants + factory
      IR.scala     -- Module, PassResult, collection helpers
```

Зависимости: `ircraft-core`, `ircraft-dialect-semantic`

## API Design

### Ops -- строить IR операции

```java
import io.alnovis.ircraft.java.Ops;

// Interface
var iface = Ops.iface("ProtoWrapper")
    .method(Ops.method("getName", Types.STRING).abstractPublic().build())
    .method(Ops.method("toBytes", Types.BYTES).abstractPublic().build())
    .javadoc("Base interface")
    .build();

// Class
var cls = Ops.cls("AbstractMoney")
    .abstractClass()
    .superClass(Types.named("BaseWrapper"))
    .typeParam("PROTO")
    .method(Ops.method("extractAmount", Types.LONG).protectedAbstract().build())
    .build();

// File
var file = Ops.file("com.example.api").type(iface).build();

// Enum
var enumOp = Ops.enumClass("Status")
    .constant("UNKNOWN", 0)
    .constant("ACTIVE", 1)
    .method(Ops.method("getValue", Types.INT).build())
    .build();

// Field
var field = Ops.staticFinalField("VERSION", Types.STRING, "\"v1\"");

// Constructor
var ctor = Ops.privateConstructor();
```

### Expr -- Expression/Statement/Block

```java
import io.alnovis.ircraft.java.Expr;

// Literals & references
Expr.literal("42", Types.INT)
Expr.identifier("proto")
Expr.thisRef()
Expr.nullLiteral()

// Method calls
Expr.call("getName")                           // no receiver
Expr.call(receiver, "getName")                 // with receiver
Expr.call(receiver, "equals", List.of(arg))    // with args

// Operators
Expr.add(left, right)
Expr.eq(left, right)
Expr.and(left, right)
Expr.mul(left, right)

// Other
Expr.fieldAccess(receiver, "proto")
Expr.cast(expr, Types.named("Money"))

// Statements
Expr.returnStmt(expr)
Expr.returnVoid()
Expr.ifStmt(condition, thenBlock)
Expr.ifElse(condition, thenBlock, elseBlock)

// Block
Expr.block(stmt1, stmt2, stmt3)   // varargs-safe for Java
Expr.block(List.of(stmt1, stmt2)) // List overload
```

### Types -- TypeRef constants + factory

```java
import io.alnovis.ircraft.java.Types;

Types.STRING    // TypeRef.STRING
Types.INT       // TypeRef.INT
Types.LONG      // TypeRef.LONG
Types.BOOL      // TypeRef.BOOL
Types.BYTES     // TypeRef.BYTES
Types.FLOAT     // TypeRef.FLOAT
Types.DOUBLE    // TypeRef.DOUBLE
Types.VOID      // TypeRef.VOID

Types.named("com.example.Money")  // TypeRef.NamedType
Types.list(Types.STRING)          // TypeRef.ListType
Types.map(Types.STRING, Types.INT) // TypeRef.MapType
```

### IR -- Module, PassResult, collection helpers

```java
import io.alnovis.ircraft.java.IR;

// Create Module
var module = IR.module("proto-wrapper", List.of(op1, op2));

// Create PassResult (success)
var result = IR.passResult(module);

// Create PassResult with diagnostics
var result = IR.passResult(module, List.of(diagnostic));

// Get topLevel as Java List
List<Operation> ops = IR.topLevel(module);

// Get methods from InterfaceOp as Java List
List<MethodOp> methods = IR.methods(iface);

// Get types from FileOp as Java List
List<Operation> types = IR.types(file);

// Append to module topLevel
var newModule = IR.appendTopLevel(module, List.of(newFile));

// Modifiers
IR.PUBLIC       // Modifier.Public
IR.ABSTRACT     // Modifier.Abstract
IR.PRIVATE      // Modifier.Private
IR.PROTECTED    // Modifier.Protected
IR.STATIC       // Modifier.Static
IR.FINAL        // Modifier.Final
IR.OVERRIDE     // Modifier.Override

// Modifier sets
Set.of(IR.PUBLIC, IR.ABSTRACT)
```

## Пример Pass на Java с фасадом

```java
package io.alnovis.protowrapper.ircraft.passes;

import io.alnovis.ircraft.core.*;
import io.alnovis.ircraft.java.*;

public class ProtoWrapperPassJava implements Pass {

    @Override public String name() { return "proto-wrapper-interface"; }
    @Override public String description() { return "Generates ProtoWrapper base interface"; }

    @Override
    public PassResult run(Module module, PassContext context) {
        String apiPackage = "com.example.api";
        for (var op : IR.topLevel(module)) {
            if (op instanceof FileOp file) {
                for (var t : IR.types(file)) {
                    if (t instanceof InterfaceOp) {
                        apiPackage = file.packageName();
                        break;
                    }
                }
            }
        }

        var protoWrapper = Ops.iface("ProtoWrapper")
            .javadoc("Base interface for all proto wrapper types.")
            .method(Ops.method("getTypedProto", Types.named("com.google.protobuf.Message"))
                .abstractPublic().javadoc("Returns the underlying proto message.").build())
            .method(Ops.method("getWrapperVersionId", Types.STRING)
                .abstractPublic().build())
            .method(Ops.method("toBytes", Types.BYTES)
                .abstractPublic().build())
            .build();

        var wrapperFile = Ops.file(apiPackage).type(protoWrapper).build();

        // Update message interfaces to extend ProtoWrapper
        var updated = new java.util.ArrayList<Operation>();
        for (var op : IR.topLevel(module)) {
            if (op instanceof FileOp file) {
                var types = new java.util.ArrayList<Operation>();
                for (var t : IR.types(file)) {
                    if (t instanceof InterfaceOp iface
                            && !iface.name().equals("ProtoWrapper")) {
                        types.add(Ops.rebuildIface(iface).addExtends(Types.named("ProtoWrapper")).build());
                    } else {
                        types.add(t);
                    }
                }
                updated.add(Ops.file(file.packageName()).types(types).build());
            } else {
                updated.add(op);
            }
        }
        updated.add(wrapperFile);

        return IR.passResult(IR.module(module.name(), updated));
    }
}
```

## Принципы проектирования

1. **Не дублировать Scala API** -- фасад дополняет, не заменяет
2. **Чистый Java** -- никаких Scala types в сигнатурах (List, не scala.collection.immutable.List)
3. **Immutable результаты** -- builders возвращают Scala case class instances
4. **Минимальный API** -- только то что реально нужно для написания passes
5. **Static methods** -- нет состояния, всё через static factory
6. **Один import** -- `io.alnovis.ircraft.java.*` покрывает 90% случаев

## Реализация

Каждый object в `ircraft-java-api` -- Scala singleton с методами, принимающими
Java types (java.util.List, java.util.Set, String) и возвращающими Scala case classes.
Conversion скрыта внутри.

```scala
// Пример реализации Ops.iface()
object Ops:
  def iface(name: String): InterfaceOpBuilder = InterfaceOpBuilder.create(name)
  def method(name: String, returnType: TypeRef): MethodOpBuilder = MethodOpBuilder.create(name, returnType)
  def file(packageName: String): FileOpBuilder = FileOpBuilder.create(packageName)
  // ...
```

Для Expr -- обёртки над Expression/Statement с Java List<> вместо Scala varargs:

```scala
object Expr:
  def call(name: String): Expression.MethodCall =
    Expression.MethodCall(None, name)

  def block(stmts: java.util.List[Statement]): Block =
    import scala.jdk.CollectionConverters.*
    Block(stmts.asScala.toList)
  // ...
```

## Откат костылей в Scala коде

При создании java-api модуля необходимо откатить Java-facing изменения,
добавленные в Scala модули до решения о фасаде. Scala модули должны содержать
только чистый Scala API.

### Что откатить в ircraft:

| Файл | Что убрать | Куда переносится |
|------|-----------|-----------------|
| `dialects/semantic/.../ops/Builders.scala` | Весь файл (InterfaceOpBuilder, ClassOpBuilder, MethodOpBuilder, FileOpBuilder, EnumClassOpBuilder) | `ircraft-java-api/.../java/Ops.scala` -- builders переезжают в фасад |
| `dialects/semantic/.../expr/Expressions.scala` | Java-facing методы: `blockFromList(java.util.List)`, `callWithArgs`, все методы принимающие `java.util.List` | `ircraft-java-api/.../java/Expr.scala` -- Java-facing factory |
| `dialects/semantic/.../expr/Expressions.scala` | `object BinOps` целиком | `ircraft-java-api/.../java/Expr.scala` или `Types.scala` |
| `dialects/semantic/.../ops/Builders.scala` | `modifiers(java.util.Set)` overloads | Фасад скрывает conversion внутри |
| `dialects/semantic/.../ops/Builders.scala` | `addTypes(java.util.List)` на FileOpBuilder | Фасад скрывает conversion внутри |

### Что убрать в proto-wrapper-plugin:

| Файл | Что убрать | Замена |
|------|-----------|--------|
| `proto-wrapper-core/.../passes/ScalaHelper.java` | Весь файл | `io.alnovis.ircraft.java.IR` + `Types` + `Expr` |
| `proto-wrapper-core/.../passes/PassHelper.java` | Весь файл | `io.alnovis.ircraft.java.IR.findApiPackage()` или аналог |

### Что остаётся в Scala модулях (чистый Scala API):

- `Expression`, `Statement`, `Block` -- sealed traits / case classes (без изменений)
- `BinOperator`, `UnOperator` -- Scala enums (без изменений)
- `InterfaceOp`, `ClassOp`, `MethodOp` и т.д. -- case classes с `@targetName("create")` smart constructors
- `Block.of(stmts: Statement*)` -- Scala varargs (удобен из Scala, не из Java)
- Все default parameter values -- работают из Scala, не из Java

### Результат:

```
ircraft-core/           -- pure Scala: Operation, Module, Pass, Pipeline, TypeRef
dialects/semantic/      -- pure Scala: InterfaceOp, ClassOp, Expression, Statement
ircraft-java-api/       -- Java facade: Ops, Expr, Types, IR (Scala implementation, Java-facing API)
```

Scala пользователь: `import io.alnovis.ircraft.dialect.semantic.ops.*` -- чистый Scala
Java пользователь: `import io.alnovis.ircraft.java.*` -- чистый Java через фасад

### Переписка passes в plugin:

Все 7 Java passes в plugin (`ProtoWrapperPassJava`, `ProtocolVersionsPassJava`,
`ConflictResolutionPassJava`, `CommonMethodsPassJava` + 3 оставшихся) переписываются
через фасад. Текущие версии с прямыми Scala вызовами заменяются на чистый код
через `Ops`, `Expr`, `Types`, `IR`.

## Оценка

~1 день на модуль + API. После этого оставшиеся 3 passes и переписка первых 4
будут значительно проще.
