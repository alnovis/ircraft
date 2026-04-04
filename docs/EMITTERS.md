# Emitters Guide

## How Emission Works

Emission is two-phase:

1. **Phase 1**: `BaseEmitter` traverses Semantic IR, calls `LanguageSyntax` hooks, builds `CodeNode` tree
2. **Phase 2**: `Renderer` converts `CodeNode` tree to text (pure, stack-safe)

All language-specific decisions are in `LanguageSyntax` + `TypeMapping`. `BaseEmitter` contains only generic traversal logic.

## Built-in Emitters

### Java

```scala
import io.alnovis.ircraft.emitters.java.JavaEmitter

val emitter = JavaEmitter[IO]
val files: IO[Map[Path, String]] = emitter(module)
```

Output: `.java` files with `public class`, `interface`, `enum`, semicolons, `new`, Java generics `<T>`.

### Scala 3

```scala
import io.alnovis.ircraft.emitters.scala.{ScalaEmitter, ScalaEmitterConfig, ScalaTarget, EnumStyle}

val emitter = ScalaEmitter.scala3[IO]
// or with custom config:
val emitter = ScalaEmitter[IO](ScalaEmitterConfig(
  scalaVersion = ScalaTarget.Scala3,
  enumStyle = EnumStyle.Scala3Enum,
  useNewKeyword = false,
))
```

Output: `.scala` files with `trait`, `enum`, `def`, `val`, `Option[T]`, `match`, `=>`, no semicolons.

Features:
- `trait` instead of `interface`
- `enum Color(val value: Int) { case Red extends Color(1) }`
- `def name: String` (no `get` prefix, camelCase)
- `val x: Int` (type after name)
- `Foo(x)` instead of `new Foo(x)`
- `x.asInstanceOf[Foo]` instead of `((Foo) x)`
- `if c then t else f` (Scala 3 syntax)
- `x => body` instead of `(x) -> body`
- `def x: Int = expr` (equals-style for single-expression methods)
- `Option[T]`, `List[E]`, `Array[Byte]`, `Any`, `Unit`
- No imports for stdlib collections
- Pattern matching via `match` (not if-chain)
- Enum prefix stripping: `TEST_ENUM_UNKNOWN` -> `Unknown`
- `Doc` rendered as Scaladoc `/** ... */`

### Scala 2

```scala
val emitter = ScalaEmitter.scala2[IO]
```

Differences from Scala 3:
- `sealed trait` + `case object` instead of `enum`
- `sealed abstract class(val value: Int)` for valued enums
- `new Foo(x)` (explicit `new`)
- `if (cond) t else f` (parenthesized condition)

## Creating a Custom Emitter

To add a new target language (e.g., Kotlin, TypeScript, Rust):

### 1. Implement TypeMapping

```scala
object KotlinTypeMapping extends TypeMapping:
  def typeName(t: TypeExpr): String = t match
    case Primitive.Bool    => "Boolean"
    case Primitive.Int32   => "Int"
    case Primitive.Str     => "String"
    case ListOf(e)         => "List<${typeName(e)}>"
    case Optional(inner)   => "${typeName(inner)}?"
    // ...

  def imports(t: TypeExpr): Set[String] = ...
```

### 2. Implement LanguageSyntax

```scala
object KotlinSyntax extends LanguageSyntax:
  val fileExtension = "kt"
  val statementTerminator = ""

  def typeSignature(...) = ...  // "data class Foo", "interface Foo"
  def funcSignature(...) = ...  // "fun getX(): Int"
  def fieldDecl(...) = ...      // "val x: Int"
  def enumVariant(...) = ...    // "RED(1),"
  def newExpr(...) = ...        // "Foo(x)"
  def castExpr(...) = ...       // "(x as Foo)"
  def lambdaExpr(...) = ...     // "{ x -> body }"
  // ...
```

### 3. Create Emitter

```scala
class KotlinEmitter[F[_]: Monad] extends BaseEmitter[F]:
  protected val syntax = KotlinSyntax
  protected val tm = KotlinTypeMapping

object KotlinEmitter:
  def apply[F[_]: Monad]: KotlinEmitter[F] = new KotlinEmitter[F]
```

That's it. All traversal logic is inherited from `BaseEmitter`. You only provide syntax and type mapping.

## LanguageSyntax Reference

### File structure
| Method | Description | Java | Scala |
|--------|-------------|------|-------|
| `fileExtension` | Output file extension | `"java"` | `"scala"` |
| `statementTerminator` | Line terminator | `";"` | `""` |
| `packageDecl(pkg)` | Package declaration | `"package com.x"` | `"package com.x"` |

### Type declarations
| Method | Description | Java | Scala |
|--------|-------------|------|-------|
| `typeSignature(...)` | Type header | `"public class Foo"` | `"class Foo"` |
| `enumSignature(...)` | Enum header | `"public enum Foo"` | `"enum Foo(val value: Int)"` |
| `enumVariant(...)` | Enum constant | `"RED(1),"` | `"case Red extends Foo(1)"` |

### Fields and functions
| Method | Description | Java | Scala |
|--------|-------------|------|-------|
| `fieldDecl(...)` | Field declaration | `"public final int x;"` | `"val x: Int"` |
| `funcSignature(...)` | Method signature | `"public int getX()"` | `"def x: Int"` |
| `paramDecl(name, type)` | Parameter | `"int x"` | `"x: Int"` |

### Expressions
| Method | Java | Scala |
|--------|------|-------|
| `newExpr(type, args)` | `"new Foo(x)"` | `"Foo(x)"` |
| `castExpr(expr, type)` | `"((Foo) x)"` | `"x.asInstanceOf[Foo]"` |
| `ternaryExpr(c, t, f)` | `"(c ? t : f)"` | `"if c then t else f"` |
| `lambdaExpr(p, body)` | `"(x) -> body"` | `"x => body"` |

### Naming
| Method | Description | Java | Scala |
|--------|-------------|------|-------|
| `transformMethodName(name)` | API method names | identity | strip `get`, camelCase |
| `transformFieldName(name)` | Field names | identity | camelCase |
| `useFuncEqualsStyle` | `= expr` body style | `false` | `true` |

### Pattern matching
| Method | Description | Java | Scala |
|--------|-------------|------|-------|
| `supportsNativeMatch` | Has match syntax | `false` | `true` |
| `matchHeader(expr)` | Match header | unused | `"expr match"` |
| `patternTypeTest(n, t)` | Type pattern | `"n instanceof T"` | `"n: T"` |
| `patternWildcard` | Wildcard | `"default"` | `"_"` |

### Documentation
| Method | Description |
|--------|-------------|
| `renderDoc(doc)` | Render `Doc` to comment. Default: `/** ... */` (Javadoc/Scaladoc). Override for `///` (Rust), `"""` (Python), etc. |

## CodeNode Tree

The intermediate representation between IR and text:

```
CodeNode
  +-- File(header, imports, body)
  +-- TypeBlock(signature, sections)
  +-- Func(signature, body?)
  +-- IfElse(cond, then, else?)
  +-- MatchBlock(expr, cases)
  +-- ForLoop, WhileLoop
  +-- SwitchBlock, TryCatch
  +-- Line(text), Block(children), Comment(text), Blank
```

Test at CodeNode level for structural assertions (not string fragility):

```scala
val tree = emitter.toFileTree("com.example", decl)
tree match
  case CodeNode.File(_, _, Vector(CodeNode.TypeBlock(sig, sections))) =>
    assert(sig.contains("trait User"))
    assertEquals(sections.size, 2)
```
