# Dialects Guide

## What is a Dialect

A dialect is your source schema -- the "A" in `A -> IR -> B`. It's just Scala case classes. ircraft doesn't require any base traits or registration.

## Creating a Source Dialect

### Step 1: Define your ADT

```scala
// Your domain -- no ircraft imports needed
case class RestApi(
  name: String,
  baseUrl: String,
  endpoints: Vector[Endpoint],
  models: Vector[Model],
)

case class Endpoint(
  path: String,
  method: String,       // GET, POST, ...
  requestBody: Option[String],  // model name
  responseType: String,         // model name
  description: String,
)

case class Model(
  name: String,
  fields: Vector[ModelField],
)

case class ModelField(
  name: String,
  fieldType: String,
  required: Boolean,
  description: String,
)
```

### Step 2: Write a Lowering

Lowering converts your ADT to ircraft IR. It's `Kleisli[F, Source, Module]`:

```scala
import cats.*
import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.ir.*

val restLowering: Lowering[Id, RestApi] = Lowering.pure { api =>
  // Models -> TypeDecl
  val modelUnits = api.models.map { model =>
    CompilationUnit(
      namespace = s"com.example.${api.name.toLowerCase}.model",
      declarations = Vector(Decl.TypeDecl(
        name = model.name,
        kind = TypeKind.Product,
        fields = model.fields.map { f =>
          val baseType = mapType(f.fieldType)
          val fieldType = if f.required then baseType else TypeExpr.Optional(baseType)
          Field(
            name = f.name,
            fieldType = fieldType,
            meta = Meta.empty.set(Doc.key, Doc(summary = f.description)),
          )
        }
      ))
    )
  }

  // Endpoints -> Protocol (service interface)
  val serviceUnit = CompilationUnit(
    namespace = s"com.example.${api.name.toLowerCase}.service",
    declarations = Vector(Decl.TypeDecl(
      name = s"${api.name}Service",
      kind = TypeKind.Protocol,
      functions = api.endpoints.map { ep =>
        val params = ep.requestBody.map(b =>
          Vector(Param("body", TypeExpr.Named(b)))
        ).getOrElse(Vector.empty)
        Func(
          name = endpointName(ep),
          params = params,
          returnType = TypeExpr.Named(ep.responseType),
          meta = Meta.empty.set(Doc.key, Doc(
            summary = ep.description,
            returns = Some(ep.responseType),
          )),
        )
      },
    ))
  )

  Module(api.name, modelUnits :+ serviceUnit)
}

def mapType(t: String): TypeExpr = t match
  case "string"  => TypeExpr.STR
  case "integer" => TypeExpr.INT
  case "long"    => TypeExpr.LONG
  case "boolean" => TypeExpr.BOOL
  case "double"  => TypeExpr.DOUBLE
  case other     => TypeExpr.Named(other)

def endpointName(ep: Endpoint): String =
  ep.method.toLowerCase + ep.path.split("/").filter(_.nonEmpty)
    .map(_.capitalize).mkString
```

### Step 3: Write domain-specific passes

```scala
// Add base URL as companion constant
val addBaseUrl = Pass.pure[Id]("add-base-url") { module =>
  // ... add ConstDecl with base URL to service type
  module
}

// Add HTTP annotations
val addHttpAnnotations = Pass.pure[Id]("http-annotations") { module =>
  // ... add @GET("/path"), @POST("/path") annotations to functions
  module
}
```

### Step 4: Compose and emit

```scala
val pipeline = Pipeline.of(addBaseUrl, addHttpAnnotations)
val module = restLowering(myApi)
val enriched = Pipeline.run(pipeline, module)
val files = ScalaEmitter.scala3[Id].apply(enriched)
```

## Lowering with Effects

### With Outcome (warnings)

```scala
val lowering: Lowering[Outcome[Id, *], RestApi] = Lowering { api =>
  val warnings = api.endpoints.filter(_.description.isEmpty).map(ep =>
    Diagnostic(Severity.Warning, s"Endpoint ${ep.path} has no description"))

  val module = buildModule(api)

  NonEmptyChain.fromSeq(warnings) match
    case Some(ws) => Outcome.warnAll(ws, module)
    case None     => Outcome.ok(module)
}
```

### With IO

```scala
val lowering: Lowering[IO, Path] = Lowering { path =>
  for
    content <- IO.blocking(Files.readString(path))
    schema  <- IO.fromEither(parseSchema(content))
  yield buildModule(schema)
}
```

## IR Reference

### Decl (declarations)

| Type | Use for |
|------|---------|
| `Decl.TypeDecl(name, kind, fields, functions, nested, supertypes)` | Class, trait, struct |
| `Decl.EnumDecl(name, variants, functions)` | Enumeration |
| `Decl.FuncDecl(func)` | Top-level function |
| `Decl.ConstDecl(name, constType, value)` | Constant |
| `Decl.AliasDecl(name, target)` | Type alias |

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
TypeExpr.STR                          // String
TypeExpr.INT                          // Int / int
TypeExpr.LONG                         // Long / long
TypeExpr.BOOL                         // Boolean / boolean
TypeExpr.BYTES                        // Array[Byte] / byte[]
TypeExpr.VOID                         // Unit / void
TypeExpr.Primitive.Any                // Any / Object
TypeExpr.Named("com.example.Money")   // Named reference
TypeExpr.Local("Money")               // Same-package reference
TypeExpr.Imported("com.x.Money", "Money")  // Cross-package with import
TypeExpr.Unresolved("proto.FQN")      // Must be resolved before emission
TypeExpr.ListOf(TypeExpr.STR)         // List[String] / List<String>
TypeExpr.MapOf(TypeExpr.STR, TypeExpr.INT)  // Map[String, Int]
TypeExpr.Optional(TypeExpr.STR)       // Option[String] / String (nullable)
TypeExpr.SetOf(TypeExpr.INT)          // Set[Int]
TypeExpr.FuncType(Vector(TypeExpr.STR), TypeExpr.INT)  // String => Int
TypeExpr.Union(Vector(TypeExpr.STR, TypeExpr.INT))     // String | Int
```

### Expr (expressions)

```scala
Expr.Lit("42", TypeExpr.INT)          // literal
Expr.Ref("name")                      // variable reference
Expr.This / Expr.Super / Expr.Null    // keywords
Expr.Access(expr, "field")            // a.b
Expr.Call(Some(recv), "method", args) // recv.method(args)
Expr.New(typeExpr, args)              // new T(args) / T(args)
Expr.Cast(expr, typeExpr)             // (T) x / x.asInstanceOf[T]
Expr.BinOp(left, op, right)           // a + b, a == b
Expr.Lambda(params, body)             // (x) -> body / x => body
```

### Stmt (statements)

```scala
Stmt.Return(Some(expr))               // return x
Stmt.Let("x", TypeExpr.INT, Some(init))  // val x: Int = init
Stmt.Assign(target, value)            // x = value
Stmt.If(cond, thenBody, elseBody)     // if/else
Stmt.Match(expr, cases)               // match / if-chain
Stmt.ForEach(v, type, iter, body)     // for loop
Stmt.Throw(expr)                      // throw
Stmt.Comment("text")                  // inline comment
```

### Pattern (for Stmt.Match)

```scala
Pattern.TypeTest("x", TypeExpr.Named("Foo"))  // case x: Foo =>
Pattern.Literal(Expr.Lit("42", TypeExpr.INT)) // case 42 =>
Pattern.Binding("x")                           // case x =>
Pattern.Wildcard                                // case _ =>
```

## Meta: Extensible Metadata

Attach arbitrary typed data to any IR node via `Meta`:

```scala
// Define a key (identity-based, type-safe)
val sourceTable = Meta.Key[String]("source.table")

// Set in lowering
Field("id", TypeExpr.LONG, meta = Meta.empty.set(sourceTable, "users"))

// Read in pass
field.meta.get(sourceTable)  // Some("users")
```

### Doc (structured documentation)

```scala
val doc = Doc(
  summary = "User account.",
  description = Some("Represents a registered user in the system."),
  params = Vector("id" -> "unique identifier"),
  returns = Some("the user"),
  tags = Vector("since" -> "1.0"),
)

// Attach via Meta
Decl.TypeDecl("User", TypeKind.Product, meta = Meta.empty.set(Doc.key, doc))

// Emitter renders as Javadoc / Scaladoc / rustdoc automatically
```

## Merge: Multi-Version Schemas

For schemas that evolve across versions (e.g., proto v1 + v2):

```scala
import io.alnovis.ircraft.core.merge.*

// Define conflict resolution strategy
val strategy = new MergeStrategy[Id]:
  def onConflict(conflict: Conflict): Outcome[Id, Resolution] =
    conflict.kind match
      case ConflictKind.FuncReturnType =>
        Outcome.warn(s"Type conflict in ${conflict.declName}", Resolution.UseType(conflict.versions.head._2))
      case _ =>
        Outcome.ok(Resolution.Skip)

// Merge N versioned modules
val merged: Outcome[Id, Module] = Merge.merge(
  NonEmptyVector.of(("v1", moduleV1), ("v2", moduleV2)),
  strategy
)
```

Merge detects conflicts (same field, different types) and delegates resolution to your strategy.
