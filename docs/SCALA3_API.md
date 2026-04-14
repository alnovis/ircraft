# Scala 3 API Guide

Complete API reference for using ircraft from Scala 3. Covers the FP-MLIR architecture,
extensible dialect system, recursion schemes, and code generation pipeline.

> For Scala 2, see [Scala 2 API Guide](SCALA2_API.md).
> For Java consumers, see [Java API Guide](JAVA_API.md).

## Dependency

```scala
// build.sbt
libraryDependencies ++= Seq(
  "io.alnovis" %% "ircraft-core"          % "2.0.0-alpha.2",
  "io.alnovis" %% "ircraft-emit"          % "2.0.0-alpha.2",
  "io.alnovis" %% "ircraft-emitter-java"  % "2.0.0-alpha.2",
  "io.alnovis" %% "ircraft-emitter-scala" % "2.0.0-alpha.2",
  "io.alnovis" %% "ircraft-dialect-proto" % "2.0.0-alpha.2", // optional
)
```

## Quick Start

```scala
import cats.*
import cats.data.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.core.ir.SemanticF.*
import io.alnovis.ircraft.core.algebra.*
import io.alnovis.ircraft.emitters.java.JavaEmitter

// 1. Define source
case class Table(name: String, columns: Vector[(String, TypeExpr)])

// 2. Lowering: source -> IR
val lowering = Lowering.pure[Id, Vector[Table]] { tables =>
  Module("sql", tables.map { t =>
    CompilationUnit("com.example.model", Vector(
      Decl.typeDecl(name = t.name, kind = TypeKind.Product,
        fields = t.columns.map((n, te) => Field(n, te)))
    ))
  })
}

// 3. Pass: IR -> IR
val addDoc = Pass.pure[Id]("add-doc") { module =>
  module.copy(units = module.units.map { u =>
    u.copy(declarations = u.declarations.map { d =>
      val m = SemanticF.meta(d.unfix).set(Doc.key, Doc(s"Generated from SQL"))
      Fix[SemanticF](SemanticF.withMeta(d.unfix, m))
    })
  })
}

// 4. Compose and run
val pipeline = Pipeline.of(addDoc)
val tables = Vector(
  Table("User", Vector("id" -> TypeExpr.Primitive.Int64, "name" -> TypeExpr.Primitive.Str))
)
val module = lowering(tables)
val enriched = Pipeline.run(pipeline, module)
val files = JavaEmitter[Id].apply(enriched)
// files: Map[Path, String] with User.java
```

---

## Core Concepts

| Concept | Type | Description |
|---------|------|-------------|
| `Fix[F]` | `case class Fix[F[_]](unfix: F[Fix[F]])` | Fixpoint -- recursive IR tree from functor F |
| `SemanticF[+A]` | `enum` | Core IR functor: TypeDeclF, EnumDeclF, FuncDeclF, AliasDeclF, ConstDeclF |
| `Module[D]` | `case class` | IR tree root with compilation units |
| `Pass[F]` | `Kleisli[F, Module[Fix[SemanticF]], ...]` | Composable IR transformation |
| `Lowering[F, S]` | `Kleisli[F, S, Module[Fix[SemanticF]]]` | Source-to-IR conversion |
| `Outcome[F, A]` | `IorT[F, NonEmptyChain[Diagnostic], A]` | Type alias for three-state result |
| `Coproduct[F, G, A]` | `enum` / `:+:` | Disjoint union of dialect functors |
| `Inject[F, G]` | typeclass | Type-safe injection into coproducts |
| `scheme.cata/ana/hylo` | functions | Stack-safe recursion schemes |
| `eliminate.dialect` | function | Type-safe coproduct shrinking |

`F[_]` is tagless final: `Id` for tests, `IO` for production.

---

## Semantic IR

The core IR is `SemanticF[+A]` -- a Scala 3 enum with five variants:

```scala
enum SemanticF[+A]:
  case TypeDeclF(name: String, kind: TypeKind, fields: Vector[Field] = Vector.empty,
    functions: Vector[Func] = Vector.empty, nested: Vector[A] = Vector.empty,
    supertypes: Vector[TypeExpr] = Vector.empty, typeParams: Vector[TypeParam] = Vector.empty,
    visibility: Visibility = Visibility.Public, annotations: Vector[Annotation] = Vector.empty,
    meta: Meta = Meta.empty) extends SemanticF[A]
  case EnumDeclF(name: String, variants: Vector[EnumVariant] = Vector.empty, ...)
  case FuncDeclF(func: Func, meta: Meta = Meta.empty)
  case AliasDeclF(name: String, target: TypeExpr, ...)
  case ConstDeclF(name: String, constType: TypeExpr, value: Expr, ...)
```

### Smart Constructors

Use `Decl.*` to create `Fix[SemanticF]` nodes:

```scala
val user = Decl.typeDecl("User", TypeKind.Product,
  fields = Vector(
    Field("id", TypeExpr.Primitive.Int64),
    Field("name", TypeExpr.Primitive.Str),
    Field("email", TypeExpr.Optional(TypeExpr.Primitive.Str))
  ),
  functions = Vector(
    Func("validate", returnType = TypeExpr.Primitive.Bool)
  )
)

val status = Decl.enumDecl("Status",
  variants = Vector(EnumVariant("Active"), EnumVariant("Inactive")))

val getId = Decl.funcDecl(Func("getId", returnType = TypeExpr.Primitive.Int64))

val alias = Decl.aliasDecl("UserId", TypeExpr.Primitive.Int64)

val maxRetries = Decl.constDecl("MAX_RETRIES", TypeExpr.Primitive.Int32,
  Expr.Lit("3", TypeExpr.Primitive.Int32))
```

### Pattern Matching on IR

```scala
decl match
  case Decl.TypeDecl(td) => println(s"Type: ${td.name}, ${td.fields.size} fields")
  case Decl.EnumDecl(ed) => println(s"Enum: ${ed.name}, ${ed.variants.size} variants")
  case Decl.FuncDecl(fd) => println(s"Func: ${fd.func.name}")
  case Decl.AliasDecl(ad) => println(s"Alias: ${ad.name} = ${ad.target}")
  case Decl.ConstDecl(cd) => println(s"Const: ${cd.name}")
```

### Accessors

```scala
val name: String = SemanticF.name(decl.unfix)    // works on any variant
val meta: Meta = SemanticF.meta(decl.unfix)
val updated: Fix[SemanticF] = Fix(SemanticF.withMeta(decl.unfix, newMeta))
```

---

## Module and CompilationUnit

```scala
val unit = CompilationUnit("com.example.model", Vector(user, status))
val module = Module("my-project", Vector(unit))

// Access
module.units.flatMap(_.declarations).foreach { decl =>
  println(SemanticF.name(decl.unfix))
}

// Map over declarations
val renamed = module.mapDecls { d =>
  val n = SemanticF.name(d.unfix)
  Fix[SemanticF](SemanticF.withMeta(d.unfix,
    SemanticF.meta(d.unfix).set(Doc.key, Doc(s"Renamed: $n"))))
}
```

---

## Passes and Pipeline

### Creating Passes

```scala
// Pure pass (no effects)
val addGetters = Pass.pure[Id]("add-getters") { module =>
  // transform module...
  module
}

// Effectful pass with Outcome
val validate = Pass[([A] =>> Outcome[Id, A])]("validate") { module =>
  val unresolved = module.units.flatMap(_.declarations).filter { d =>
    d.unfix match
      case TypeDeclF(_, _, fields, _, _, _, _, _, _, _) =>
        fields.exists(f => f.fieldType.isInstanceOf[TypeExpr.Unresolved])
      case _ => false
  }
  if unresolved.nonEmpty then
    Outcome.fail(s"Unresolved types in: ${unresolved.map(d => SemanticF.name(d.unfix)).mkString(", ")}")
  else
    Outcome.ok(module)
}
```

### Composing Pipelines

```scala
val pipeline = Pipeline.of(addGetters, validate)
val result: Outcome[Id, Module[Fix[SemanticF]]] = Pipeline.run(pipeline, module)
```

### Outcome (Error Handling)

`Outcome[F, A]` is a type alias for `IorT[F, NonEmptyChain[Diagnostic], A]`:

```scala
// Type alias (Scala 3 only)
type Outcome[F[_], A] = IorT[F, NonEmptyChain[Diagnostic], A]

// Smart constructors (shared, work on all Scala versions)
Outcome.ok(module)              // clean success
Outcome.warn("deprecated", module)  // success with warning
Outcome.fail("unresolved type")     // error
```

---

## Lowering

```scala
// From Proto
import io.alnovis.ircraft.dialects.proto.*
val protoModule = ProtoLowering.lower[Id](protoFile)

// Custom lowering
val sqlLowering = Lowering.pure[Id, Vector[Table]] { tables =>
  Module("sql", tables.map(tableToUnit))
}
val module = sqlLowering(tables)
```

---

## Emitters

```scala
import io.alnovis.ircraft.emitters.java.JavaEmitter
import io.alnovis.ircraft.emitters.scala.ScalaEmitter

// Java output
val javaFiles: Map[Path, String] = JavaEmitter[Id].apply(module)

// Scala 3 output
val scala3Files = ScalaEmitter.scala3[Id].apply(module)

// Scala 2 output
val scala2Files = ScalaEmitter.scala2[Id].apply(module)
```

---

## Meta (Typed Metadata)

`Meta` is an opaque type with extension methods:

```scala
// Define keys (identity-based, like vault)
val sourceKey = Meta.Key[String]("source")
val versionKey = Meta.Key[Int]("version")

// Create
val meta = Meta.of(Meta.entry(sourceKey, "proto"), Meta.entry(versionKey, 3))

// Query (extension methods)
meta.get(sourceKey)       // Option[String] = Some("proto")
meta.contains(versionKey) // true
meta.set(sourceKey, "sql")
meta.remove(versionKey)
meta.isEmpty              // false
meta.keys                 // Set[Meta.Key[?]]

// Doc integration
val doc = Doc("User representation", description = Some("Stores user data"))
val withDoc = meta.set(Doc.key, doc)
```

---

## Merge

```scala
import cats.data.NonEmptyVector
import io.alnovis.ircraft.core.merge.*

// Define strategy
val strategy = new MergeStrategy[Id]:
  def onConflict(conflict: Conflict) =
    conflict.kind match
      case ConflictKind.FieldType =>
        Outcome.ok(Resolution.UseType(conflict.versions.head._2))
      case ConflictKind.Missing =>
        Outcome.ok(Resolution.Skip)
      case _ =>
        Outcome.fail(s"Cannot resolve: ${conflict.declName}.${conflict.memberName}")

// Merge
val versions = NonEmptyVector.of(("v1", moduleV1), ("v2", moduleV2))
val merged: Outcome[Id, Module[Fix[SemanticF]]] = Merge.merge[Id](versions, strategy)
```

---

## FP-MLIR: Extensible IR

The architecture is based on "Data Types a la Carte" -- open-world extensibility
via functor coproducts.

### Fix[F] -- Recursive Trees

`Fix[F]` ties the recursive knot for any functor F:

```scala
// A tree of SemanticF nodes
val tree: Fix[SemanticF] = Fix(TypeDeclF("User", TypeKind.Product,
  fields = Vector(Field("id", TypeExpr.Primitive.Int64)),
  nested = Vector(
    Fix(TypeDeclF("Address", TypeKind.Product,
      fields = Vector(Field("city", TypeExpr.Primitive.Str))))
  )
))
```

### Recursion Schemes

Stack-safe tree traversals:

```scala
// cata: fold bottom-up (like a tree reduce)
val countNodes: Fix[SemanticF] => Int = scheme.cata[SemanticF, Int] {
  case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => 1 + nested.sum
  case EnumDeclF(_, _, _, _, _, _, _)               => 1
  case FuncDeclF(_, _)                              => 1
  case AliasDeclF(_, _, _, _)                       => 1
  case ConstDeclF(_, _, _, _, _)                    => 1
}
countNodes(tree)  // 2 (User + Address)

// ana: unfold from a seed
val buildChain: Int => Fix[SemanticF] = scheme.ana[SemanticF, Int] { n =>
  if n <= 0 then TypeDeclF(s"Leaf", TypeKind.Product)
  else TypeDeclF(s"Node$n", TypeKind.Product, nested = Vector(n - 1))
}

// hylo: unfold then fold without building intermediate tree
val sumChain: Int => Int = scheme.hylo[SemanticF, Int, Int](
  alg = { case TypeDeclF(_, _, _, _, nested, _, _, _, _, _) => 1 + nested.sum; case _ => 1 },
  coalg = { n =>
    if n <= 0 then TypeDeclF("L", TypeKind.Product)
    else TypeDeclF(s"N$n", TypeKind.Product, nested = Vector(n - 1))
  }
)
```

### Coproduct and :+: -- Combining Dialects

```scala
// Type alias for coproduct
type :+:[F[_], G[_]] = [A] =>> Coproduct[F, G, A]

// Mixed IR: Proto + Semantic nodes in one tree
type ProtoIR = ProtoF :+: SemanticF

// Build mixed trees via Inject
val injP = Inject[ProtoF, ProtoIR]
val injS = Inject[SemanticF, ProtoIR]

val mixed: Fix[ProtoIR] = Fix(injP.inj(
  ProtoF.MessageNodeF("User",
    fields = Vector(Field("id", TypeExpr.Primitive.Int64)),
    nested = Vector(
      Fix(injS.inj(TypeDeclF("Nested", TypeKind.Product)))
    ))
))
```

### eliminate.dialect -- Type-safe Coproduct Shrinking

```scala
import io.alnovis.ircraft.dialects.proto.ProtoDialect

// ProtoF -> SemanticF lowering algebra
val protoToSemantic: Algebra[ProtoF, Fix[SemanticF]] = ProtoDialect.protoToSemantic

// Eliminate ProtoF from the coproduct
val pure: Fix[SemanticF] = eliminate.dialect(protoToSemantic).apply(mixed)
// Result: Fix[SemanticF] with no ProtoF nodes
```

### Creating a Custom Dialect

Three steps: define functor, provide Traverse, provide DialectInfo + trait mixins.

```scala
// 1. Define the functor
enum SqlF[+A]:
  case TableNodeF(name: String, columns: Vector[Field], nested: Vector[A], meta: Meta = Meta.empty)
  case ViewNodeF(name: String, query: String, meta: Meta = Meta.empty)
  case IndexNodeF(name: String, table: String, columns: Vector[String], meta: Meta = Meta.empty)

// 2. Provide Traverse instance
object SqlF:
  given Traverse[SqlF] with
    def traverse[G[_]: Applicative, A, B](fa: SqlF[A])(f: A => G[B]): G[SqlF[B]] = fa match
      case TableNodeF(n, c, nested, m) => nested.traverse(f).map(ns => TableNodeF(n, c, ns, m))
      case ViewNodeF(n, q, m)          => Applicative[G].pure(ViewNodeF(n, q, m))
      case IndexNodeF(n, t, c, m)      => Applicative[G].pure(IndexNodeF(n, t, c, m))

    def foldLeft[A, B](fa: SqlF[A], b: B)(f: (B, A) => B): B = fa match
      case TableNodeF(_, _, nested, _) => nested.foldLeft(b)(f)
      case _                           => b

    def foldRight[A, B](fa: SqlF[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = fa match
      case TableNodeF(_, _, nested, _) => Traverse[Vector].foldRight(nested, lb)(f)
      case _                           => lb

  // 3. DialectInfo + trait mixins
  given DialectInfo[SqlF] = DialectInfo("SqlF", 3)

  given HasName[SqlF] with
    def name[A](fa: SqlF[A]): String = fa match
      case TableNodeF(n, _, _, _)  => n
      case ViewNodeF(n, _, _)      => n
      case IndexNodeF(n, _, _, _)  => n

  given HasNested[SqlF] with
    def nested[A](fa: SqlF[A]): Vector[A] = fa match
      case TableNodeF(_, _, ns, _) => ns
      case _                       => Vector.empty

  given HasMeta[SqlF] with
    def meta[A](fa: SqlF[A]): Meta = fa match
      case TableNodeF(_, _, _, m)  => m
      case ViewNodeF(_, _, m)      => m
      case IndexNodeF(_, _, _, m)  => m
    def withMeta[A](fa: SqlF[A], m: Meta): SqlF[A] = fa match
      case t: TableNodeF[A @unchecked] => t.copy(meta = m)
      case v: ViewNodeF[A @unchecked]  => v.copy(meta = m)
      case i: IndexNodeF[A @unchecked] => i.copy(meta = m)

  given HasFields[SqlF] with
    def fields[A](fa: SqlF[A]): Vector[Field] = fa match
      case TableNodeF(_, cols, _, _) => cols
      case _                         => Vector.empty

  given HasMethods[SqlF] with
    def functions[A](fa: SqlF[A]): Vector[Func] = Vector.empty

  given HasVisibility[SqlF] with
    def visibility[A](fa: SqlF[A]): Visibility = Visibility.Public
```

### Using a Custom Dialect

```scala
// Mixed IR type
type SqlIR = SqlF :+: SemanticF

// Build tree
val injSql = Inject[SqlF, SqlIR]
val table: Fix[SqlIR] = Fix(injSql.inj(
  SqlF.TableNodeF("users", Vector(Field("id", TypeExpr.Primitive.Int64)), Vector.empty)
))

// Lowering algebra
val sqlToSemantic: Algebra[SqlF, Fix[SemanticF]] = {
  case SqlF.TableNodeF(n, cols, nested, m) =>
    Decl.typeDecl(n, TypeKind.Product, fields = cols, nested = nested, meta = m)
  case SqlF.ViewNodeF(n, _, m) =>
    Decl.typeDecl(n, TypeKind.Protocol, meta = m)
  case SqlF.IndexNodeF(n, _, _, m) =>
    Decl.constDecl(n, TypeExpr.Primitive.Str, Expr.Lit(n, TypeExpr.Primitive.Str), meta = m)
}

// Eliminate
val pure: Fix[SemanticF] = eliminate.dialect(sqlToSemantic).apply(table)

// Generic passes work on mixed IR via trait mixins
import io.alnovis.ircraft.core.algebra.CoproductInstances.given
val names: Vector[String] = scheme.cata[SqlIR, Vector[String]] { fa =>
  val selfName = Vector(HasName[SqlIR].name(fa))
  val childNames = Traverse[SqlIR].foldLeft(fa, Vector.empty[String])(_ ++ _)
  selfName ++ childNames
}.apply(table)
```

### Trait Mixins for Generic Passes

```scala
// Works on ANY dialect with HasName + Traverse
def collectAllNames[F[_]: Traverse: HasName]: Fix[F] => Vector[String] =
  scheme.cata[F, Vector[String]] { fa =>
    Vector(HasName[F].name(fa)) ++ Traverse[F].foldLeft(fa, Vector.empty[String])(_ ++ _)
  }

// Works on ProtoF, SqlF, SemanticF, ProtoF :+: SemanticF, etc.
collectAllNames[SemanticF].apply(someDecl)
collectAllNames[ProtoF :+: SemanticF].apply(mixedTree)
```

---

## Constraint System

Type-safe compile-time guarantees on IR properties.

```scala
// Built-in constraints
trait MustBeResolved extends Constraint
trait MustNotBeEmpty extends Constraint

// Verify via cata
val unresolvedTypes: Vector[Diagnostic] =
  ConstraintVerifier.verifyFieldTypes[SemanticF, MustBeResolved].apply(tree)

val emptyNames: Vector[Diagnostic] =
  ConstraintVerifier.verifyNames[SemanticF, MustNotBeEmpty].apply(tree)

// Constrained wrapper (Scala 3 only: !> syntax)
val verified: Fix[SemanticF] !> MustBeResolved = Constrained(tree)
verified.verify  // Vector[Diagnostic]

// Custom constraint
trait MustHaveDoc extends Constraint
given ConstraintVerify[MustHaveDoc, Meta] =
  ConstraintVerify.instance { meta =>
    if meta.contains(Doc.key) then Vector.empty
    else Vector(Diagnostic(Severity.Warning, "Missing documentation"))
  }
```

---

## TypeExpr Reference

```scala
// Primitives
TypeExpr.Primitive.Bool
TypeExpr.Primitive.Int8 / Int16 / Int32 / Int64
TypeExpr.Primitive.UInt8 / UInt16 / UInt32 / UInt64
TypeExpr.Primitive.Float32 / Float64
TypeExpr.Primitive.Str / Char / Bytes / Void / Any

// Convenience aliases
TypeExpr.STR  // = Primitive.Str
TypeExpr.BOOL // = Primitive.Bool
TypeExpr.INT  // = Primitive.Int32
TypeExpr.LONG // = Primitive.Int64

// Collections and generics
TypeExpr.ListOf(TypeExpr.Primitive.Str)
TypeExpr.MapOf(TypeExpr.Primitive.Str, TypeExpr.Primitive.Int32)
TypeExpr.Optional(TypeExpr.Primitive.Str)
TypeExpr.SetOf(TypeExpr.Primitive.Int64)
TypeExpr.TupleOf(Vector(TypeExpr.Primitive.Str, TypeExpr.Primitive.Int32))

// Named / reference types
TypeExpr.Named("com.example.User")
TypeExpr.Local("T")            // type parameter reference
TypeExpr.Imported("com.example", "User")
TypeExpr.Unresolved("foo.Bar") // unresolved -- should be eliminated by passes

// Advanced
TypeExpr.Applied(TypeExpr.Named("List"), Vector(TypeExpr.Primitive.Str))
TypeExpr.FuncType(Vector(TypeExpr.Primitive.Str), TypeExpr.Primitive.Bool)
TypeExpr.Union(Vector(TypeExpr.Primitive.Str, TypeExpr.Primitive.Int32))
TypeExpr.Intersection(Vector(TypeExpr.Named("Serializable"), TypeExpr.Named("Comparable")))
```

---

## Complete Example: SQL Tables -> Java Code

```scala
import cats.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*
import io.alnovis.ircraft.core.algebra.*
import io.alnovis.ircraft.emitters.java.JavaEmitter
import java.nio.file.{Files, Path}

// Source model
case class Column(name: String, sqlType: String)
case class SqlTable(name: String, columns: Vector[Column])

// Type mapping
def mapSqlType(sqlType: String): TypeExpr = sqlType.toUpperCase match
  case "BIGINT"             => TypeExpr.Primitive.Int64
  case "INTEGER" | "INT"    => TypeExpr.Primitive.Int32
  case "VARCHAR" | "TEXT"   => TypeExpr.Primitive.Str
  case "BOOLEAN"            => TypeExpr.Primitive.Bool
  case "DOUBLE" | "FLOAT"   => TypeExpr.Primitive.Float64
  case "TIMESTAMP"          => TypeExpr.Named("java.time.Instant")
  case other                => TypeExpr.Named(other)

// Lowering
val lowering = Lowering.pure[Id, Vector[SqlTable]] { tables =>
  Module("sql-gen", tables.map { t =>
    CompilationUnit("com.example.model", Vector(
      Decl.typeDecl(
        name = t.name,
        kind = TypeKind.Product,
        fields = t.columns.map(c => Field(c.name, mapSqlType(c.sqlType))),
        meta = Meta.of(Meta.entry(Doc.key, Doc(s"Generated from SQL table ${t.name}")))
      )
    ))
  })
}

// Run
val tables = Vector(
  SqlTable("User", Vector(
    Column("id", "BIGINT"), Column("name", "VARCHAR"),
    Column("email", "VARCHAR"), Column("created_at", "TIMESTAMP")
  )),
  SqlTable("Order", Vector(
    Column("id", "BIGINT"), Column("user_id", "BIGINT"),
    Column("total", "DOUBLE"), Column("status", "VARCHAR")
  ))
)

val module = lowering(tables)
val files = JavaEmitter[Id].apply(module)

files.foreach { (path, source) =>
  println(s"=== $path ===")
  println(source)
}
```

Output:
```java
// com/example/model/User.java
package com.example.model;

import java.time.Instant;

/**
 * Generated from SQL table User
 */
public class User {
    public Long id;
    public String name;
    public String email;
    public Instant createdAt;
}
```
