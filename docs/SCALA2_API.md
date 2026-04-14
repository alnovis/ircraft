# Scala 2 API Guide

ircraft is a pure functional codegen framework with full cross-compilation support for
Scala 2.12, 2.13, and 3.x. This guide covers the Scala 2 API surface -- sealed traits
instead of enums, `implicit` instead of `given`, kind-projector for type lambdas, and
brace-based syntax throughout.

> **Dependency** (sbt):
> ```scala
> libraryDependencies ++= Seq(
>   "io.alnovis" %% "ircraft-core"         % "2.0.0-alpha.2",
>   "io.alnovis" %% "ircraft-emit"         % "2.0.0-alpha.2",
>   "io.alnovis" %% "ircraft-emitter-java" % "2.0.0-alpha.2",
>   // Scala emitter (optional):
>   "io.alnovis" %% "ircraft-emitter-scala" % "2.0.0-alpha.2",
> )
>
> // Required: kind-projector for type lambdas
> addCompilerPlugin("org.typelevel" % "kind-projector" % "0.13.3" cross CrossVersion.full)
> ```

## Quick Start

Full pipeline: define IR -> apply pass -> emit Java source code.

```scala
import cats.Id
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.emitters.java.JavaEmitter

// 1. Build IR declarations
val user: Fix[SemanticF] = Decl.typeDecl(
  name = "User",
  kind = TypeKind.Product,
  fields = Vector(
    Field("id", TypeExpr.LONG),
    Field("name", TypeExpr.STR)
  )
)

// 2. Wrap in a module
val module: Module[Fix[SemanticF]] = Module(
  "my-project",
  Vector(CompilationUnit("com.example.model", Vector(user)))
)

// 3. Apply a pass
val addTimestamp: Pass[Id] = Pass.pure[Id]("add-timestamp") { m => m }
val enriched: Module[Fix[SemanticF]] = Pipeline.run(addTimestamp, module)

// 4. Emit Java source
val files: Map[java.nio.file.Path, String] = new JavaEmitter[Id].apply(enriched)
files.foreach { case (path, source) =>
  println(s"=== $path ===\n$source")
}
```

---

## Core Concepts

| Concept | Type | Purpose |
|---------|------|---------|
| `Fix[F]` | `Fix[F[_]]` | Recursive tree -- ties the knot: `Fix[F] = F[Fix[F]]` |
| `SemanticF` | `sealed trait SemanticF[+A]` | Standard IR dialect functor (5 node kinds) |
| `Decl` | `object Decl` | Smart constructors for `Fix[SemanticF]` |
| `Module[D]` | `case class Module[D]` | Root of the IR tree -- name + compilation units |
| `CompilationUnit[D]` | `case class CompilationUnit[D]` | Namespace (package) + declarations |
| `Pass[F]` | `Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]` | Module transformation |
| `Lowering[F, S]` | `Kleisli[F, S, Module[Fix[SemanticF]]]` | Source schema to IR conversion |
| `Pipeline` | `object Pipeline` | Pass composition and execution |
| `Meta` | `final class Meta` | Type-safe metadata store (identity-keyed) |
| `Outcome` | `object Outcome` | Smart constructors for `IorT[F, NonEmptyChain[Diagnostic], A]` |
| `Coproduct[F, G, A]` | `sealed trait` | Disjoint union of two functors (FP-MLIR) |
| `Inject[F, G]` | `trait Inject[F[_], G[_]]` | Witness that F can be injected into G |
| `scheme` | `object scheme` | Stack-safe recursion schemes (cata, ana, hylo) |
| `Constrained[A, C]` | `case class` | Value tagged with a constraint marker |

---

## Semantic IR

`SemanticF` is the standard dialect functor. It is a `sealed trait` parameterized by `+A`,
where `A` marks recursive children (nested declarations).

```scala
sealed trait SemanticF[+A]

object SemanticF {
  final case class TypeDeclF[+A](name: String, kind: TypeKind, fields: Vector[Field],
    functions: Vector[Func], nested: Vector[A], supertypes: Vector[TypeExpr],
    typeParams: Vector[TypeParam], visibility: Visibility, annotations: Vector[Annotation],
    meta: Meta) extends SemanticF[A]

  final case class EnumDeclF[+A](name: String, variants: Vector[EnumVariant],
    functions: Vector[Func], supertypes: Vector[TypeExpr], visibility: Visibility,
    annotations: Vector[Annotation], meta: Meta) extends SemanticF[A]

  final case class FuncDeclF[+A](func: Func, meta: Meta) extends SemanticF[A]
  final case class AliasDeclF[+A](name: String, target: TypeExpr, visibility: Visibility,
    meta: Meta) extends SemanticF[A]
  final case class ConstDeclF[+A](name: String, constType: TypeExpr, value: Expr,
    visibility: Visibility, meta: Meta) extends SemanticF[A]
}
```

### Decl Smart Constructors

`Decl` provides ergonomic factory methods that return `Fix[SemanticF]`:

```scala
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._

// Class with fields
val user: Fix[SemanticF] = Decl.typeDecl(
  name = "User",
  kind = TypeKind.Product,
  fields = Vector(
    Field("id", TypeExpr.LONG),
    Field("name", TypeExpr.STR),
    Field("email", TypeExpr.Optional(TypeExpr.STR))
  )
)

// Enum
val status: Fix[SemanticF] = Decl.enumDecl(
  name = "Status",
  variants = Vector(
    EnumVariant("ACTIVE"),
    EnumVariant("INACTIVE"),
    EnumVariant("SUSPENDED")
  )
)

// Top-level function
val greet: Fix[SemanticF] = Decl.funcDecl(
  Func("greet", params = Vector(Param("name", TypeExpr.STR)), returnType = TypeExpr.STR)
)

// Constant
val maxRetries: Fix[SemanticF] = Decl.constDecl(
  "MAX_RETRIES",
  TypeExpr.INT,
  Expr.Lit("3", TypeExpr.INT)
)

// Type alias
val UserId: Fix[SemanticF] = Decl.aliasDecl("UserId", TypeExpr.LONG)
```

### Decl Extractors (Pattern Matching)

```scala
val decl: Fix[SemanticF] = Decl.typeDecl("User", TypeKind.Product)

decl match {
  case Decl.TypeDecl(td)  => println(s"type: ${td.name}, kind: ${td.kind}")
  case Decl.EnumDecl(ed)  => println(s"enum: ${ed.name}")
  case Decl.FuncDecl(fd)  => println(s"func: ${fd.func.name}")
  case Decl.AliasDecl(ad) => println(s"alias: ${ad.name} -> ${ad.target}")
  case Decl.ConstDecl(cd) => println(s"const: ${cd.name}")
}
```

### Declaration Kinds

| Factory | SemanticF variant | Represents |
|---------|------------------|------------|
| `Decl.typeDecl(...)` | `TypeDeclF` | class, interface, struct |
| `Decl.enumDecl(...)` | `EnumDeclF` | enum type |
| `Decl.funcDecl(...)` | `FuncDeclF` | standalone function |
| `Decl.aliasDecl(...)` | `AliasDeclF` | type alias |
| `Decl.constDecl(...)` | `ConstDeclF` | constant value |

### TypeKind

| Kind | Java | Scala | Rust |
|------|------|-------|------|
| `Product` | class | class | struct |
| `Protocol` | interface | trait | trait |
| `Abstract` | abstract class | abstract class | -- |
| `Sum` | sealed interface | sealed trait / enum | enum |
| `Singleton` | final class | object | -- |

### TypeExpr

```scala
TypeExpr.STR                                    // String
TypeExpr.INT                                    // Int / int
TypeExpr.LONG                                   // Long / long
TypeExpr.BOOL                                   // Boolean / boolean
TypeExpr.DOUBLE                                 // Double / double
TypeExpr.BYTES                                  // Array[Byte] / byte[]
TypeExpr.VOID                                   // Unit / void
TypeExpr.Primitive.Any                          // Any / Object
TypeExpr.Named("com.example.Money")             // Named reference (FQN)
TypeExpr.Local("Money")                         // Same-package reference
TypeExpr.Imported("com.x.Money", "Money")       // Cross-package with import
TypeExpr.Unresolved("proto.FQN")                // Must be resolved before emission
TypeExpr.ListOf(TypeExpr.STR)                   // List[String] / List<String>
TypeExpr.MapOf(TypeExpr.STR, TypeExpr.INT)      // Map[String, Int]
TypeExpr.Optional(TypeExpr.STR)                 // Option[String] / String (nullable)
TypeExpr.SetOf(TypeExpr.INT)                    // Set[Int]
TypeExpr.FuncType(Vector(TypeExpr.STR), TypeExpr.INT)  // String => Int
TypeExpr.Union(Vector(TypeExpr.STR, TypeExpr.INT))     // String | Int
```

---

## Module and CompilationUnit

`Module[D]` is the root container. `CompilationUnit[D]` groups declarations by namespace (package).

```scala
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._

val unit = CompilationUnit(
  namespace = "com.example.model",
  declarations = Vector(
    Decl.typeDecl("User", TypeKind.Product, fields = Vector(
      Field("id", TypeExpr.LONG),
      Field("name", TypeExpr.STR)
    )),
    Decl.enumDecl("Status", variants = Vector(
      EnumVariant("ACTIVE"), EnumVariant("INACTIVE")
    ))
  )
)

val module: Module[Fix[SemanticF]] = Module("my-project", Vector(unit))

// Access
val name: String = module.name                                  // "my-project"
val units: Vector[CompilationUnit[Fix[SemanticF]]] = module.units
val allDecls: Vector[Fix[SemanticF]] = units.flatMap(_.declarations)
```

---

## Passes and Pipeline

A `Pass[F]` is `Kleisli[F, Module[Fix[SemanticF]], Module[Fix[SemanticF]]]` -- a module
transformation inside effect `F`.

### Creating Passes

```scala
import cats.Id
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._

// Pure pass (no effects, no errors)
val rename: Pass[Id] = Pass.pure[Id]("rename-fields") { module =>
  // transform module here
  module
}

// Identity pass (no-op)
val noop: Pass[Id] = Pass.id[Id]

// Pass with effect (e.g., IorT for warnings/errors)
import cats.data.{IorT, NonEmptyChain}

type Result[A] = IorT[Id, NonEmptyChain[Diagnostic], A]

val validate: Pass[Result] = Pass[Result]("validate") { module =>
  val decls = module.units.flatMap(_.declarations)
  if (decls.isEmpty) {
    Outcome.fail[Id, Module[Fix[SemanticF]]]("module has no declarations")
  } else {
    Outcome.ok[Id, Module[Fix[SemanticF]]](module)
  }
}
```

### Composing with Pipeline

```scala
val pipeline: Pass[Id] = Pipeline.of[Id](rename, noop)
val result: Module[Fix[SemanticF]] = Pipeline.run(pipeline, module)
```

### Conditional Passes

```scala
import cats.Monad

val passes: Vector[(Pass[Id], Boolean)] = Vector(
  (rename, true),
  (noop, false)   // disabled
)
val conditional: Pass[Id] = Pipeline.build[Id](passes)
```

### Error Propagation

- If a pass in an `IorT`-based pipeline returns `Ior.Left`, subsequent passes are skipped.
- Warnings (`Ior.Both`) from all passes accumulate.

---

## Lowering

Lowering converts a source schema to IR. It is `Kleisli[F, Source, Module[Fix[SemanticF]]]`.

```scala
import cats.Id
import cats.data.Kleisli
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._

// Define source schema (plain case classes -- no ircraft imports needed)
case class SqlTable(name: String, columns: Vector[SqlColumn])
case class SqlColumn(name: String, sqlType: String, nullable: Boolean)

// Map SQL types to IR types
def mapSqlType(sqlType: String): TypeExpr = sqlType.toUpperCase match {
  case "BIGINT"              => TypeExpr.LONG
  case "VARCHAR" | "TEXT"    => TypeExpr.STR
  case "BOOLEAN"             => TypeExpr.BOOL
  case "DOUBLE"              => TypeExpr.DOUBLE
  case other                 => TypeExpr.Named(other)
}

// Create lowering
val tableLowering: Lowering[Id, Vector[SqlTable]] = Kleisli { tables =>
  val units = tables.map { table =>
    val fields = table.columns.map { col =>
      val baseType = mapSqlType(col.sqlType)
      val fieldType = if (col.nullable) TypeExpr.Optional(baseType) else baseType
      Field(col.name, fieldType)
    }
    CompilationUnit(
      namespace = "com.example.model",
      declarations = Vector(Decl.typeDecl(table.name, TypeKind.Product, fields = fields))
    )
  }
  Module("sql", units)
}

val module: Module[Fix[SemanticF]] = tableLowering.run(Vector(
  SqlTable("User", Vector(
    SqlColumn("id", "BIGINT", nullable = false),
    SqlColumn("name", "VARCHAR", nullable = false),
    SqlColumn("email", "VARCHAR", nullable = true)
  ))
))
```

### Lowering with Warnings

```scala
import cats.Id
import cats.data.{IorT, NonEmptyChain, Kleisli}
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.ir._

type Result[A] = IorT[Id, NonEmptyChain[Diagnostic], A]

val loweringWithWarnings: Lowering[Result, Vector[SqlTable]] = Kleisli { tables =>
  val warnings = tables.flatMap { t =>
    t.columns.filter(_.sqlType.toUpperCase == "TEXT").map { col =>
      Diagnostic(Severity.Warning, s"Column ${t.name}.${col.name} uses TEXT -- consider VARCHAR")
    }
  }

  val module = buildModule(tables) // your logic here

  NonEmptyChain.fromSeq(warnings) match {
    case Some(ws) => Outcome.warnAll[Id, Module[Fix[SemanticF]]](ws, module)
    case None     => Outcome.ok[Id, Module[Fix[SemanticF]]](module)
  }
}
```

---

## Emitters

Emitters convert IR to source code files. Three built-in targets are provided.

```scala
import cats.Id
import io.alnovis.ircraft.emitters.java.JavaEmitter
import io.alnovis.ircraft.emitters.scala.{ScalaEmitter, ScalaEmitterConfig, ScalaTarget, EnumStyle}

// Java
val javaFiles = new JavaEmitter[Id].apply(module)

// Scala 3
val scala3Files = ScalaEmitter.scala3[Id].apply(module)

// Scala 2
val scala2Files = ScalaEmitter.scala2[Id].apply(module)
// Equivalent to:
val scala2Explicit = new ScalaEmitter[Id](ScalaEmitterConfig(
  scalaVersion = ScalaTarget.Scala2,
  enumStyle = EnumStyle.SealedTrait,
  useNewKeyword = true
)).apply(module)

// Write to disk
import java.nio.file.{Files, Path}

javaFiles.foreach { case (path, source) =>
  Files.createDirectories(path.getParent)
  Files.writeString(path, source)
}
```

### Emitter Configuration (Scala)

| Setting | Default (Scala 3) | Scala 2 preset |
|---------|-------------------|----------------|
| `scalaVersion` | `ScalaTarget.Scala3` | `ScalaTarget.Scala2` |
| `enumStyle` | `EnumStyle.Scala3Enum` | `EnumStyle.SealedTrait` |
| `useNewKeyword` | `false` | `true` |

---

## Merge

Merges multiple versions of the same schema into one IR module. Conflicts (same field,
different types across versions) are delegated to your strategy.

```scala
import cats.Id
import cats.data.{IorT, NonEmptyChain, NonEmptyVector}
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.merge._

// Define conflict resolution strategy
val strategy: MergeStrategy[Id] = new MergeStrategy[Id] {
  def onConflict(conflict: Conflict): IorT[Id, NonEmptyChain[Diagnostic], Resolution] = {
    conflict.kind match {
      case ConflictKind.FieldType =>
        Outcome.warn[Id, Resolution](
          s"Type conflict in ${conflict.declName}.${conflict.memberName}",
          Resolution.UseType(conflict.versions.head._2)
        )
      case ConflictKind.FuncReturnType =>
        Outcome.ok[Id, Resolution](Resolution.Skip)
      case _ =>
        Outcome.ok[Id, Resolution](Resolution.Skip)
    }
  }
}

// Merge N versioned modules
val merged: IorT[Id, NonEmptyChain[Diagnostic], Module[Fix[SemanticF]]] =
  Merge.merge[Id](
    NonEmptyVector.of(("v1", moduleV1), ("v2", moduleV2)),
    strategy
  )
```

### Resolution Options

| Resolution | Effect |
|-----------|--------|
| `Resolution.UseType(typeExpr)` | Use the specified type for the conflicting member |
| `Resolution.DualAccessor(types)` | Keep version-specific accessors (stored in Meta) |
| `Resolution.Custom(decls)` | Replace with custom declarations |
| `Resolution.Skip` | Drop the conflicting member |

### Merge Metadata Keys

After merge, nodes carry metadata about their origin:

```scala
import io.alnovis.ircraft.core.merge.Merge

decl.unfix match {
  case td: SemanticF.TypeDeclF[_] =>
    td.meta.get(Merge.Keys.presentIn)      // Some(Vector("v1", "v2"))
    td.meta.get(Merge.Keys.sources)         // Some(Vector("v1", "v2"))
    td.meta.get(Merge.Keys.conflictType)    // Some("RESOLVED") if conflict was resolved
    td.meta.get(Merge.Keys.typePerVersion)  // Some(Map("v1" -> TypeExpr.INT, ...))
}
```

---

## FP-MLIR: Extensible IR

For domains where the standard IR (`SemanticF`) is not expressive enough, ircraft supports
**open-world extensibility** via Data Types a la Carte. Define your dialect as a functor,
mix it with `SemanticF` via a coproduct, and progressively lower it.

### When to Use This

- Your domain has constructs that do not map cleanly to `Decl` (e.g., SQL constraints,
  GraphQL directives, Terraform providers)
- You want **type-safe progressive lowering** -- the compiler guarantees your dialect is
  fully eliminated before emission
- You are building a reusable dialect that others can compose with their own

For simple schema-to-code scenarios, the standard Lowering approach (above) is sufficient.

### Fix[F] and Recursive Trees

`Fix[F]` ties the recursive knot for any functor `F`:

```scala
import io.alnovis.ircraft.core.algebra.Fix

// Fix[F] = F[Fix[F]]
final case class Fix[F[_]](unfix: F[Fix[F]])

// Example: wrap a SemanticF node
val node: Fix[SemanticF] = Fix[SemanticF](
  TypeDeclF("User", TypeKind.Product, fields = Vector(Field("id", TypeExpr.LONG)))
)
// Equivalently via smart constructor:
val same: Fix[SemanticF] = Decl.typeDecl("User", TypeKind.Product,
  fields = Vector(Field("id", TypeExpr.LONG)))
```

### Coproduct with kind-projector

`Coproduct[F, G, A]` is the disjoint union of two functors. On Scala 2, use kind-projector
for the partially-applied type:

```scala
import io.alnovis.ircraft.core.algebra.{Coproduct, Fix}

// Coproduct[F, G, *] is a type lambda: Lambda[A => Coproduct[F, G, A]]
// kind-projector rewrites `*` to the proper type lambda

// Define a mixed IR type
type MyIR[A] = Coproduct[SqlF, SemanticF, A]

// Or equivalently with a type alias:
type SqlIR = Coproduct[SqlF, SemanticF, *]

// Trees of this type can contain EITHER SqlF or SemanticF nodes:
val tree: Fix[Coproduct[SqlF, SemanticF, *]] = ???
```

`Coproduct` has two cases:

```scala
sealed trait Coproduct[F[_], G[_], A]
object Coproduct {
  final case class Inl[F[_], G[_], A](fa: F[A]) extends Coproduct[F, G, A]  // left
  final case class Inr[F[_], G[_], A](ga: G[A]) extends Coproduct[F, G, A]  // right
}
```

A `Traverse` instance for the coproduct is derived automatically when both `F` and `G`
have `Traverse` instances (via `Coproduct.coproductTraverse`).

### Inject[F, G] with Implicit Instances

`Inject` witnesses that functor `F` can be embedded into functor `G`:

```scala
import io.alnovis.ircraft.core.algebra.{Coproduct, Inject}

// Three rules provide implicit instances:
// 1. refl:  Inject[F, F]                              -- F injects into itself
// 2. left:  Inject[F, Coproduct[F, G, *]]             -- F is the left of the coproduct
// 3. right: Inject[F, G] => Inject[F, Coproduct[H, G, *]]  -- recursive on right

type IR[A] = Coproduct[SqlF, SemanticF, A]

// Summon instances
val injSql: Inject[SqlF, Coproduct[SqlF, SemanticF, *]] =
  Inject.left[SqlF, SemanticF]

val injSem: Inject[SemanticF, Coproduct[SqlF, SemanticF, *]] =
  Inject.right[SemanticF, SemanticF, SqlF]

// Inject nodes into the coproduct
val sqlNode: Coproduct[SqlF, SemanticF, Fix[IR]] =
  injSql.inj(TableNodeF[Fix[IR]]("users", Vector.empty))

val semNode: Coproduct[SqlF, SemanticF, Fix[IR]] =
  injSem.inj(TypeDeclF[Fix[IR]]("User", TypeKind.Product))

// Wrap in Fix
val tree: Fix[IR] = Fix[IR](sqlNode)

// Project back (partial -- returns Option)
val maybeSql: Option[SqlF[Fix[IR]]] = injSql.prj(tree.unfix)  // Some(...)
val maybeSem: Option[SemanticF[Fix[IR]]] = injSem.prj(tree.unfix)  // None
```

### Creating a Custom Dialect

A dialect is a `sealed trait` parameterized by `+A`, where `A` marks recursive children.
You must provide `Traverse` and `DialectInfo` instances.

```scala
import cats.{Applicative, Eval, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.DialectInfo
import io.alnovis.ircraft.core.ir.{Field, Meta}

// Step 1: Define the ADT
sealed trait SqlF[+A]

object SqlF {
  final case class TableNodeF[+A](
    name: String,
    columns: Vector[Field],
    constraints: Vector[String] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  final case class ViewNodeF[+A](
    name: String,
    query: String,
    columns: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  final case class IndexNodeF[+A](
    name: String,
    tableName: String,
    columnNames: Vector[String],
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  // Step 2: Traverse instance (extends Functor)
  //
  // Rules for each field:
  //   Data   (String, Vector[Field], ...) -> copy as-is / pure(copy)
  //   A      (direct recursive)           -> f(x) / f(x)
  //   Coll[A](Vector[A], Option[A], ...)  -> .map(f) / .traverse(f)
  implicit val sqlTraverse: Traverse[SqlF] = new Traverse[SqlF] {
    def traverse[G[_], A, B](fa: SqlF[A])(f: A => G[B])(
      implicit G: Applicative[G]
    ): G[SqlF[B]] = fa match {
      case TableNodeF(n, cols, cons, nested, m) =>
        nested.traverse(f).map(ns => TableNodeF[B](n, cols, cons, ns, m))
      case ViewNodeF(n, q, cols, m) =>
        G.pure(ViewNodeF[B](n, q, cols, m))
      case IndexNodeF(n, t, cols, m) =>
        G.pure(IndexNodeF[B](n, t, cols, m))
    }

    def foldLeft[A, B](fa: SqlF[A], b: B)(f: (B, A) => B): B = fa match {
      case TableNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                              => b
    }

    def foldRight[A, B](fa: SqlF[A], lb: Eval[B])(
      f: (A, Eval[B]) => Eval[B]
    ): Eval[B] = fa match {
      case TableNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                              => lb
    }
  }

  // Step 3: DialectInfo (name + operation count)
  implicit val sqlDialectInfo: DialectInfo[SqlF] = DialectInfo("SqlF", 3)
}
```

### Traverse Instance Rules

| Field type | `traverse` | `foldLeft` | `foldRight` |
|-----------|-----------|-----------|------------|
| Data (`String`, `Int`, `Vector[Field]`, ...) | copy as-is + wrap in `G.pure(...)` | return `b` (skip) | return `lb` (skip) |
| `A` (direct recursive) | `f(x)` | `f(b, x)` | `f(x, lb)` |
| `Vector[A]` / `Option[A]` | `.traverse(f)` | `.foldLeft(b)(f)` | `.foldRight(lb)(f)` |

For cases with no `A` fields at all: `traverse` returns `G.pure(copy)`, `foldLeft` returns `b`,
`foldRight` returns `lb`.

### scheme.cata / ana / hylo

Stack-safe recursion schemes (trampolined via `cats.Eval`) over `Fix[F]`:

```scala
import cats.Traverse
import io.alnovis.ircraft.core.algebra.{Fix, scheme}
import io.alnovis.ircraft.core.algebra.Algebra._

// cata: bottom-up fold
def countNodes[F[_]](implicit T: Traverse[F]): Fix[F] => Int =
  scheme.cata[F, Int] { fa =>
    T.foldLeft(fa, 1)((acc, child) => acc + child)
  }

// ana: top-down unfold
def buildList(n: Int): Fix[SemanticF] =
  scheme.ana[SemanticF, Int] { i =>
    if (i <= 0) TypeDeclF("Leaf", TypeKind.Product)
    else TypeDeclF("Node", TypeKind.Product, nested = Vector(i - 1))
  }.apply(n)

// hylo: unfold then fold without building the intermediate tree
def sumRange: Int => Int =
  scheme.hylo[SemanticF, Int, Int](
    alg = { fa =>
      SemanticF.name(fa) match {
        case "Leaf" => 0
        case _      => Traverse[SemanticF].foldLeft(fa, 1)(_ + _)
      }
    },
    coalg = { i =>
      if (i <= 0) TypeDeclF("Leaf", TypeKind.Product)
      else TypeDeclF(s"$i", TypeKind.Product, nested = Vector(i - 1))
    }
  )
```

`scheme.cata` works with any `Traverse[F]` -- your dialect, `SemanticF`, or a coproduct of both.

### eliminate.dialect

The lowering algebra converts each dialect operation into a `SemanticF` subtree. Then
`eliminate.dialect` applies `scheme.cata` to shrink the coproduct:

```scala
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import SqlF._

// Define the lowering algebra: SqlF[Fix[SemanticF]] => Fix[SemanticF]
val sqlToSemantic: Algebra[SqlF, Fix[SemanticF]] = {
  case TableNodeF(name, columns, constraints, nested, meta) =>
    val annotations = constraints.map(c => Annotation(c))
    Fix[SemanticF](TypeDeclF(name, TypeKind.Product,
      fields = columns, nested = nested, annotations = annotations, meta = meta))
  case ViewNodeF(name, query, columns, meta) =>
    val queryKey = Meta.Key[String]("sql.query")
    Fix[SemanticF](TypeDeclF(name, TypeKind.Protocol,
      fields = columns, meta = meta.set(queryKey, query)))
  case IndexNodeF(name, tableName, columnNames, meta) =>
    Fix[SemanticF](ConstDeclF(name, TypeExpr.STR,
      Expr.Lit(s"$tableName(${columnNames.mkString(",")})", TypeExpr.STR), meta = meta))
}

// Eliminate SqlF from the coproduct
val eliminateSql: Fix[Coproduct[SqlF, SemanticF, *]] => Fix[SemanticF] =
  eliminate.dialect(sqlToSemantic)
```

**Type safety**: `eliminate.dialect` returns `Fix[SemanticF]`. Passing
`Fix[Coproduct[SqlF, SemanticF, *]]` directly to the emitter will not compile -- the
compiler forces you to eliminate all custom dialect operations first.

### Building and Eliminating a Mixed Tree

```scala
import cats.Id
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.emitters.java.JavaEmitter
import SqlF._

type IR[A] = Coproduct[SqlF, SemanticF, A]

val injSql: Inject[SqlF, Coproduct[SqlF, SemanticF, *]] =
  Inject.left[SqlF, SemanticF]
val injSem: Inject[SemanticF, Coproduct[SqlF, SemanticF, *]] =
  Inject.right[SemanticF, SemanticF, SqlF]

// Build a mixed tree
val table: Fix[IR] = Fix[IR](injSql.inj(TableNodeF[Fix[IR]](
  "User",
  columns = Vector(
    Field("id", TypeExpr.LONG, mutability = Mutability.Mutable),
    Field("name", TypeExpr.STR, mutability = Mutability.Mutable)
  )
)))

// Eliminate SqlF -> pure SemanticF
val clean: Fix[SemanticF] = eliminateSql(table)

// Now emit
val module = Module("sql-demo", Vector(
  CompilationUnit("com.example.model", Vector(clean))
))
val files = new JavaEmitter[Id].apply(module)
```

### Trait Mixins with Implicit Instances

Trait mixins (`HasName`, `HasFields`, `HasMethods`, `HasNested`, `HasMeta`, `HasVisibility`)
let you write passes that work on **any dialect** without knowing its concrete type.

Provide instances for your dialect:

```scala
import io.alnovis.ircraft.core.algebra.{HasName, HasNested, HasMeta}
import io.alnovis.ircraft.core.ir.Meta
import SqlF._

implicit val sqlHasName: HasName[SqlF] = new HasName[SqlF] {
  def name[A](fa: SqlF[A]): String = fa match {
    case TableNodeF(n, _, _, _, _) => n
    case ViewNodeF(n, _, _, _)     => n
    case IndexNodeF(n, _, _, _)    => n
  }
}

implicit val sqlHasNested: HasNested[SqlF] = new HasNested[SqlF] {
  def nested[A](fa: SqlF[A]): Vector[A] = fa match {
    case TableNodeF(_, _, _, nested, _) => nested
    case _                              => Vector.empty
  }
}

implicit val sqlHasMeta: HasMeta[SqlF] = new HasMeta[SqlF] {
  def meta[A](fa: SqlF[A]): Meta = fa match {
    case TableNodeF(_, _, _, _, m) => m
    case ViewNodeF(_, _, _, m)     => m
    case IndexNodeF(_, _, _, m)    => m
  }
  def withMeta[A](fa: SqlF[A], m: Meta): SqlF[A] = fa match {
    case t: TableNodeF[A @unchecked] => t.copy(meta = m)
    case v: ViewNodeF[A @unchecked]  => v.copy(meta = m)
    case i: IndexNodeF[A @unchecked] => i.copy(meta = m)
  }
}
```

Then write **generic passes** using trait constraints:

```scala
import cats.Traverse
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.algebra.SemanticInstances._
import io.alnovis.ircraft.core.algebra.CoproductInstances._

// Collects all names from any dialect tree
def collectAllNames[F[_]](implicit T: Traverse[F], N: HasName[F]): Fix[F] => Vector[String] =
  scheme.cata[F, Vector[String]] { fa =>
    Vector(N.name(fa)) ++ T.foldLeft(fa, Vector.empty[String])(_ ++ _)
  }

// Validates that no node has an empty name
def validateNoEmptyNames[F[_]](
  implicit T: Traverse[F], N: HasName[F]
): Fix[F] => Vector[Diagnostic] =
  scheme.cata[F, Vector[Diagnostic]] { fa =>
    val nm = N.name(fa)
    val childDiags = T.foldLeft(fa, Vector.empty[Diagnostic])(_ ++ _)
    if (nm.trim.isEmpty)
      childDiags :+ Diagnostic(Severity.Error, "Empty name found in dialect node")
    else childDiags
  }
```

These passes work **unchanged** on `Fix[SemanticF]`, `Fix[SqlF]`, or
`Fix[Coproduct[SqlF, SemanticF, *]]`:

```scala
// Same function, three different tree types:
val namesA: Vector[String] = collectAllNames[SemanticF].apply(semanticTree)
val namesB: Vector[String] = collectAllNames[SqlF].apply(sqlTree)
val namesC: Vector[String] =
  collectAllNames[Coproduct[SqlF, SemanticF, *]].apply(mixedTree)
```

Coproduct instances are auto-derived. Import `CoproductInstances._` and `SemanticInstances._`
-- if both `F` and `G` have `HasName`, then `Coproduct[F, G, *]` automatically gets `HasName`.

---

## Constraint System

The constraint system lets you tag values with invariants and verify them.

### Constrained[A, C]

```scala
import io.alnovis.ircraft.core.algebra.{Constraint, Constrained, ConstraintVerify}
import io.alnovis.ircraft.core.ir.TypeExpr
import io.alnovis.ircraft.core.Diagnostic

// Scala 2: use Constrained[A, C] (no !> syntax)
trait MustBeResolved extends Constraint

implicit val resolvedVerify: ConstraintVerify[MustBeResolved, TypeExpr] =
  ConstraintVerify.instance[MustBeResolved, TypeExpr] { t =>
    t match {
      case TypeExpr.Unresolved(fqn) =>
        Vector(Diagnostic(Severity.Error, s"Unresolved type: $fqn"))
      case _ =>
        Vector.empty
    }
  }

// Tag a value
val safe: Constrained[TypeExpr, MustBeResolved] = Constrained(TypeExpr.STR)
safe.verify  // Vector.empty -- constraint satisfied

val broken: Constrained[TypeExpr, MustBeResolved] = Constrained(TypeExpr.Unresolved("proto.Foo"))
broken.verify  // Vector(Diagnostic(Error, "Unresolved type: proto.Foo"))
```

### Custom Constraints

```scala
trait MustBeShort extends Constraint

implicit val shortVerify: ConstraintVerify[MustBeShort, String] =
  ConstraintVerify.instance[MustBeShort, String] { s =>
    if (s.length <= 10) Vector.empty
    else Vector(Diagnostic(Severity.Error, s"Too long: ${s.length} chars"))
  }

val ok: Constrained[String, MustBeShort] = Constrained("ok")
ok.verify  // Vector.empty

val bad: Constrained[String, MustBeShort] = Constrained("way too long name")
bad.verify  // Vector(Diagnostic(Error, "Too long: 17 chars"))
```

### ConstraintVerifier (Tree-Level)

Verify constraints across an entire `Fix[F]` tree:

```scala
import io.alnovis.ircraft.core.algebra.ConstraintVerifier

// Verify all field types are resolved
val fieldDiags: Vector[Diagnostic] =
  ConstraintVerifier.verifyFieldTypes[SemanticF, MustBeResolved].apply(tree)

// Verify all names satisfy a constraint
trait MustNotBeEmpty extends Constraint
implicit val nonEmptyVerify: ConstraintVerify[MustNotBeEmpty, String] =
  ConstraintVerify.instance[MustNotBeEmpty, String] { s =>
    if (s.trim.isEmpty) Vector(Diagnostic(Severity.Error, "Empty name"))
    else Vector.empty
  }

val nameDiags: Vector[Diagnostic] =
  ConstraintVerifier.verifyNames[SemanticF, MustNotBeEmpty].apply(tree)
```

Both work on any dialect with the right trait instances (`HasFields` for field verification,
`HasName` for name verification).

---

## Meta

`Meta` is a type-safe metadata store. Each key is identity-unique -- two keys with the same
name but different allocations are distinct. On Scala 2 it is a regular class with methods
(not an opaque type).

```scala
import io.alnovis.ircraft.core.ir.Meta

// Define keys (once, as vals or object members)
val authorKey: Meta.Key[String] = Meta.Key[String]("author")
val versionKey: Meta.Key[Int] = Meta.Key[Int]("version")

// Create and query
val meta: Meta = Meta.empty
  .set(authorKey, "team-a")
  .set(versionKey, 3)

val author: Option[String] = meta.get(authorKey)   // Some("team-a")
val has: Boolean = meta.contains(versionKey)        // true
val cleaned: Meta = meta.remove(authorKey)

// Meta is a Monoid -- combine merges (right wins on conflicts)
import cats.Monoid
val combined: Meta = Monoid[Meta].combine(meta1, meta2)
```

### Doc (Structured Documentation)

```scala
import io.alnovis.ircraft.core.ir.Doc

val doc = Doc(
  summary = "User account.",
  description = Some("Represents a registered user."),
  params = Vector("id" -> "unique identifier"),
  returns = Some("the user"),
  tags = Vector("since" -> "1.0")
)

// Attach via Meta
val decl = Decl.typeDecl("User", TypeKind.Product,
  meta = Meta.empty.set(Doc.key, doc))

// Emitter renders as Javadoc / Scaladoc automatically
```

---

## Error Handling

On Scala 2, use `IorT[F, NonEmptyChain[Diagnostic], A]` directly. The `Outcome` object
provides smart constructors.

**Note**: Scala 2 does not have the `Outcome[F, A]` type alias due to type inference
limitations. Use the full `IorT` type or define a local type alias.

### Smart Constructors

```scala
import cats.Id
import cats.data.{IorT, NonEmptyChain}
import io.alnovis.ircraft.core.{Diagnostic, Outcome, Severity}

type Result[A] = IorT[Id, NonEmptyChain[Diagnostic], A]

// Success
val ok: Result[Int] = Outcome.ok[Id, Int](42)

// Warning + value
val warned: Result[Int] = Outcome.warn[Id, Int]("field deprecated", 42)

// Multiple warnings
val warnedAll: Result[Int] = Outcome.warnAll[Id, Int](
  NonEmptyChain.of(
    Diagnostic(Severity.Warning, "deprecated"),
    Diagnostic(Severity.Warning, "renamed")
  ),
  42
)

// Error (no value)
val failed: Result[Int] = Outcome.fail[Id, Int]("unresolved type: Foo")

// Error from NonEmptyChain
val failedNec: Result[Int] = Outcome.failNec[Id, Int](
  NonEmptyChain.one(Diagnostic(Severity.Error, "type not found"))
)

// Lift an F[A] into IorT
val lifted: Result[Int] = Outcome.liftF[Id, Int](42: Id[Int])
```

### Inspecting Results

```scala
import cats.data.Ior

val result: Result[Module[Fix[SemanticF]]] = // ...

// IorT wraps Ior, so extract with .value (for Id) or .value (for IO, etc.)
result.value match {
  case Ior.Right(module)        => println(s"Success: ${module.name}")
  case Ior.Both(warnings, mod)  => warnings.toList.foreach(w => println(s"WARN: ${w.message}"))
  case Ior.Left(errors)         => errors.toList.foreach(e => println(s"ERROR: ${e.message}"))
}
```

### Chaining Operations

```scala
val output: Result[Map[java.nio.file.Path, String]] =
  for {
    module  <- loweringResult
    enriched <- IorT.pure[Id, NonEmptyChain[Diagnostic]](
                  Pipeline.run(pipeline, module))
    files   <- IorT.pure[Id, NonEmptyChain[Diagnostic]](
                  new JavaEmitter[Id].apply(enriched))
  } yield files
```

### Passes with IorT

On Scala 2, the type lambda syntax for `Pass` with `IorT` uses the `({type L[A] = ...})#L`
pattern:

```scala
import cats.data.{IorT, NonEmptyChain}
import io.alnovis.ircraft.core._

type Diags = NonEmptyChain[Diagnostic]

// Option 1: type alias
type Result[A] = IorT[Id, Diags, A]
val myPass: Pass[Result] = Pass.pure[Result]("my-pass") { module => module }

// Option 2: inline type lambda (classic Scala 2)
val myPass2: Pass[({type L[A] = IorT[Id, Diags, A]})#L] =
  Pass[({type L[A] = IorT[Id, Diags, A]})#L]("validate") { module =>
    Outcome.ok[Id, Module[Fix[SemanticF]]](module)
  }

// Option 3: kind-projector syntax
val myPass3: Pass[IorT[Id, Diags, *]] =
  Pass[IorT[Id, Diags, *]]("validate") { module =>
    Outcome.ok[Id, Module[Fix[SemanticF]]](module)
  }
```

---

## Cross-Compilation Notes

ircraft cross-compiles across Scala 2.12, 2.13, and 3.x. The core API is identical;
only syntax differs. Here is a summary of the differences:

| Aspect | Scala 3 | Scala 2 |
|--------|---------|---------|
| Dialect ADT | `enum SqlF[+A]` | `sealed trait SqlF[+A]` + `final case class` |
| Functor instances | `given Traverse[SqlF] with` | `implicit val: Traverse[SqlF] = new Traverse[SqlF] { ... }` |
| Coproduct type | `SqlF :+: SemanticF` | `Coproduct[SqlF, SemanticF, *]` (kind-projector) |
| Constraint syntax | `TypeExpr !> MustBeResolved` | `Constrained[TypeExpr, MustBeResolved]` |
| Outcome type alias | `Outcome[F, A]` | `IorT[F, NonEmptyChain[Diagnostic], A]` (use directly) |
| Meta | opaque type (Scala 3 only) | `final class Meta` |
| Extension methods | `extension (x: T) def foo = ...` | `implicit class XOps(x: T) { def foo = ... }` |
| Type lambdas | `[A] =>> Coproduct[F, G, A]` | `Coproduct[F, G, *]` (kind-projector) |
| Compiler plugin | none needed | `addCompilerPlugin("org.typelevel" % "kind-projector" ...)` |
| Indentation syntax | optional significant indentation | brace syntax only (`{ }`) |
| Context bounds | `def foo[F[_]: Traverse]` | `def foo[F[_]](implicit T: Traverse[F])` or `def foo[F[_]: Traverse]` |
| givens/implicits | `given Traverse[F]` | `implicit val/def` |
| Source directories | `src/main/scala-3/` | `src/main/scala-2/` |

### Shared vs Platform-Specific Code

- `src/main/scala/` -- shared code (works on all versions)
- `src/main/scala-2/` -- Scala 2 only (sealed traits, implicit instances)
- `src/main/scala-3/` -- Scala 3 only (enums, given, opaque types)

Core types like `Fix`, `Algebra`, `scheme`, `Decl`, `Pipeline`, `Pass`, `Merge`,
`Diagnostic`, `Outcome`, and all IR node types (`TypeExpr`, `Expr`, `Stmt`, `Field`,
`Func`, etc.) live in shared code and work identically on both versions.

---

## Complete Example: SQL Tables -> Java Source Code

End-to-end: define SQL tables, lower to IR via a custom dialect functor, apply passes,
eliminate the dialect, and emit Java source.

```scala
import cats.{Applicative, Eval, Id, Traverse}
import cats.syntax.all._
import io.alnovis.ircraft.core._
import io.alnovis.ircraft.core.algebra._
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.algebra.SemanticInstances._
import io.alnovis.ircraft.core.algebra.CoproductInstances._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.emitters.java.JavaEmitter

// ================================================================
// 1. Define the SQL dialect functor
// ================================================================

sealed trait SqlF[+A]

object SqlF {
  final case class TableNodeF[+A](
    name: String,
    columns: Vector[Field],
    constraints: Vector[String] = Vector.empty,
    nested: Vector[A] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  final case class ViewNodeF[+A](
    name: String,
    query: String,
    columns: Vector[Field] = Vector.empty,
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  final case class IndexNodeF[+A](
    name: String,
    tableName: String,
    columnNames: Vector[String],
    meta: Meta = Meta.empty
  ) extends SqlF[A]

  implicit val sqlTraverse: Traverse[SqlF] = new Traverse[SqlF] {
    def traverse[G[_], A, B](fa: SqlF[A])(f: A => G[B])(
      implicit G: Applicative[G]
    ): G[SqlF[B]] = fa match {
      case TableNodeF(n, cols, cons, nested, m) =>
        nested.traverse(f).map(ns => TableNodeF[B](n, cols, cons, ns, m))
      case ViewNodeF(n, q, cols, m) =>
        G.pure(ViewNodeF[B](n, q, cols, m))
      case IndexNodeF(n, t, cols, m) =>
        G.pure(IndexNodeF[B](n, t, cols, m))
    }

    def foldLeft[A, B](fa: SqlF[A], b: B)(f: (B, A) => B): B = fa match {
      case TableNodeF(_, _, _, nested, _) => nested.foldLeft(b)(f)
      case _                              => b
    }

    def foldRight[A, B](fa: SqlF[A], lb: Eval[B])(
      f: (A, Eval[B]) => Eval[B]
    ): Eval[B] = fa match {
      case TableNodeF(_, _, _, nested, _) => nested.foldRight(lb)(f)
      case _                              => lb
    }
  }

  implicit val sqlDialectInfo: DialectInfo[SqlF] = DialectInfo("SqlF", 3)

  implicit val sqlHasName: HasName[SqlF] = new HasName[SqlF] {
    def name[A](fa: SqlF[A]): String = fa match {
      case TableNodeF(n, _, _, _, _) => n
      case ViewNodeF(n, _, _, _)     => n
      case IndexNodeF(n, _, _, _)    => n
    }
  }

  implicit val sqlHasNested: HasNested[SqlF] = new HasNested[SqlF] {
    def nested[A](fa: SqlF[A]): Vector[A] = fa match {
      case TableNodeF(_, _, _, ns, _) => ns
      case _                          => Vector.empty
    }
  }
}

// ================================================================
// 2. Define the lowering algebra
// ================================================================

import SqlF._

val sqlQueryKey: Meta.Key[String] = Meta.Key[String]("sql.query")

val sqlToSemantic: Algebra[SqlF, Fix[SemanticF]] = {
  case TableNodeF(name, columns, constraints, nested, meta) =>
    val anns = constraints.map(c => Annotation(c))
    Fix[SemanticF](TypeDeclF(name, TypeKind.Product,
      fields = columns, nested = nested, annotations = anns, meta = meta))
  case ViewNodeF(name, query, columns, meta) =>
    Fix[SemanticF](TypeDeclF(name, TypeKind.Protocol,
      fields = columns, meta = meta.set(sqlQueryKey, query)))
  case IndexNodeF(name, tableName, columnNames, meta) =>
    Fix[SemanticF](ConstDeclF(name, TypeExpr.STR,
      Expr.Lit(s"$tableName(${columnNames.mkString(",")})", TypeExpr.STR), meta = meta))
}

val eliminateSql: Fix[Coproduct[SqlF, SemanticF, *]] => Fix[SemanticF] =
  eliminate.dialect(sqlToSemantic)

// ================================================================
// 3. Build a mixed IR tree
// ================================================================

type IR[A] = Coproduct[SqlF, SemanticF, A]

val injSql: Inject[SqlF, Coproduct[SqlF, SemanticF, *]] =
  Inject.left[SqlF, SemanticF]
val injSem: Inject[SemanticF, Coproduct[SqlF, SemanticF, *]] =
  Inject.right[SemanticF, SemanticF, SqlF]

val usersTable: Fix[IR] = Fix[IR](injSql.inj(TableNodeF[Fix[IR]](
  "User",
  columns = Vector(
    Field("id", TypeExpr.LONG, mutability = Mutability.Mutable),
    Field("name", TypeExpr.STR, mutability = Mutability.Mutable),
    Field("email", TypeExpr.Optional(TypeExpr.STR), mutability = Mutability.Mutable)
  ),
  constraints = Vector("PRIMARY KEY (id)")
)))

val ordersTable: Fix[IR] = Fix[IR](injSql.inj(TableNodeF[Fix[IR]](
  "Order",
  columns = Vector(
    Field("id", TypeExpr.LONG, mutability = Mutability.Mutable),
    Field("userId", TypeExpr.LONG, mutability = Mutability.Mutable),
    Field("total", TypeExpr.DOUBLE, mutability = Mutability.Mutable)
  ),
  constraints = Vector("PRIMARY KEY (id)", "FOREIGN KEY (userId) REFERENCES User(id)")
)))

// ================================================================
// 4. Generic pass on mixed IR (before elimination)
// ================================================================

def collectAllNames[F[_]](implicit T: Traverse[F], N: HasName[F]): Fix[F] => Vector[String] =
  scheme.cata[F, Vector[String]] { fa =>
    Vector(N.name(fa)) ++ T.foldLeft(fa, Vector.empty[String])(_ ++ _)
  }

val names: Vector[String] = collectAllNames[IR].apply(usersTable)
// Vector("User")

// ================================================================
// 5. Eliminate, wrap in module, apply passes, emit
// ================================================================

val cleanUser: Fix[SemanticF] = eliminateSql(usersTable)
val cleanOrder: Fix[SemanticF] = eliminateSql(ordersTable)

val module: Module[Fix[SemanticF]] = Module(
  "sql-demo",
  Vector(CompilationUnit("com.example.model", Vector(cleanUser, cleanOrder)))
)

// Add documentation pass
val addDocs: Pass[Id] = Pass.pure[Id]("add-docs") { m =>
  m.copy(units = m.units.map { unit =>
    unit.copy(declarations = unit.declarations.map { decl =>
      decl.unfix match {
        case td: TypeDeclF[Fix[SemanticF] @unchecked] =>
          val doc = Doc(summary = s"Generated from SQL table: ${td.name}")
          Fix[SemanticF](td.copy(meta = td.meta.set(Doc.key, doc)))
        case _ => decl
      }
    })
  })
}

val pipeline: Pass[Id] = Pipeline.of[Id](addDocs)
val enriched: Module[Fix[SemanticF]] = Pipeline.run(pipeline, module)

// Emit Java source
val files: Map[java.nio.file.Path, String] = new JavaEmitter[Id].apply(enriched)

files.foreach { case (path, source) =>
  println(s"=== $path ===")
  println(source)
  println()
}

// Output:
// === com/example/model/User.java ===
// package com.example.model;
//
// /**
//  * Generated from SQL table: User
//  */
// @PRIMARY KEY (id)
// public class User {
//     public long id;
//     public String name;
//     public String email;
// }
//
// === com/example/model/Order.java ===
// ...
```
