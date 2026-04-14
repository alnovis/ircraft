package io.alnovis.ircraft.emit

import io.alnovis.ircraft.core.ir._

/**
  * Defines the syntax rules and conventions for a target programming language.
  *
  * A [[LanguageSyntax]] implementation captures all language-specific formatting decisions
  * needed by [[BaseEmitter]] to construct a [[CodeNode]] tree. This includes file structure
  * (extensions, terminators), type and function declaration syntax, expression formatting,
  * naming conventions, and pattern matching support.
  *
  * Each target language (Java, Scala, Kotlin, etc.) provides its own implementation.
  *
  * @see [[io.alnovis.ircraft.emitters.java.JavaSyntax]] for the Java implementation
  * @see [[io.alnovis.ircraft.emitters.scala.ScalaSyntax]] for the Scala implementation
  * @see [[BaseEmitter]] for the emitter that consumes this syntax
  */
trait LanguageSyntax {

  // -- File structure --

  /**
    * The file extension for source files in this language (without the leading dot).
    *
    * @return the file extension (e.g., `"java"`, `"scala"`, `"kt"`)
    */
  def fileExtension: String

  /**
    * The statement terminator character(s) for this language.
    *
    * @return the terminator string (e.g., `";"` for Java, `""` for Scala)
    */
  def statementTerminator: String

  /**
    * Formats a package declaration for the given package name.
    *
    * @param pkg the fully qualified package name (e.g., `"com.example.model"`)
    * @return the package declaration text (e.g., `"package com.example.model"`)
    */
  def packageDecl(pkg: String): String

  // -- Type declarations --

  /**
    * Constructs the full signature line for a type declaration.
    *
    * Includes visibility, type keyword, name, type parameters, and supertypes.
    *
    * @param vis        the visibility modifier string (e.g., `"public "`)
    * @param kind       the kind of type being declared (protocol, abstract, product, etc.)
    * @param name       the type name
    * @param typeParams the formatted type parameter list (e.g., `"<T>"`, `"[A]"`, or `""`)
    * @param supertypes the formatted supertype names
    * @return the complete type signature string
    */
  def typeSignature(vis: String, kind: TypeKind, name: String, typeParams: String, supertypes: Vector[String]): String

  /**
    * Constructs the signature line for an enum declaration.
    *
    * @param vis        the visibility modifier string
    * @param name       the enum name
    * @param supertypes the implemented interface/trait names
    * @param hasValues  whether the enum variants carry associated values
    * @return the enum signature string
    */
  def enumSignature(vis: String, name: String, supertypes: Vector[String], hasValues: Boolean): String

  /**
    * Formats a single enum variant/constant.
    *
    * @param name     the variant name
    * @param args     the constructor arguments (e.g., `"(1)"`) or empty string
    * @param isLast   whether this is the last variant (affects trailing punctuation in some languages)
    * @param enumName the parent enum name (used by some languages for extends clauses)
    * @return the formatted enum variant line
    */
  def enumVariant(name: String, args: String, isLast: Boolean, enumName: String): String

  // -- Fields --

  /**
    * Formats a field declaration.
    *
    * @param vis      the visibility modifier string
    * @param mutable  whether the field is mutable
    * @param typeName the field's type name
    * @param name     the field name
    * @param init     an optional initializer expression
    * @return the formatted field declaration string
    */
  def fieldDecl(vis: String, mutable: Boolean, typeName: String, name: String, init: Option[String]): String

  /**
    * Formats a constant declaration.
    *
    * @param vis      the visibility modifier string
    * @param typeName the constant's type name
    * @param name     the constant name
    * @param value    the constant's value expression
    * @return the formatted constant declaration string
    */
  def constDecl(vis: String, typeName: String, name: String, value: String): String

  // -- Type aliases --

  /**
    * Formats a type alias declaration, if supported by the language.
    *
    * Returns `None` if the language does not support type aliases (e.g., Java),
    * in which case the emitter will skip the declaration.
    *
    * @param vis    the visibility modifier string
    * @param name   the alias name
    * @param target the target type name
    * @return `Some(declaration)` if supported, `None` otherwise
    */
  def aliasDecl(vis: String, name: String, target: String): Option[String] = None

  // -- Functions --

  /**
    * Constructs the full signature line for a function/method declaration.
    *
    * @param vis        the visibility modifier string
    * @param modifiers  the set of function modifiers (static, override, default, etc.)
    * @param typeParams the formatted type parameter list
    * @param returnType the formatted return type name
    * @param name       the function name
    * @param params     the formatted parameter list (e.g., `"int x, String y"`)
    * @param isAbstract whether the function has no body
    * @param parentKind the kind of the enclosing type (affects modifiers like `default` in Java interfaces)
    * @return the complete function signature string
    */
  def funcSignature(
    vis: String,
    modifiers: Set[FuncModifier],
    typeParams: String,
    returnType: String,
    name: String,
    params: String,
    isAbstract: Boolean,
    parentKind: TypeKind
  ): String

  /**
    * Formats a single parameter declaration.
    *
    * @param name     the parameter name
    * @param typeName the parameter's type name
    * @return the formatted parameter declaration (e.g., `"int x"` or `"x: Int"`)
    */
  def paramDecl(name: String, typeName: String): String

  // -- Visibility --

  /**
    * Converts an IR visibility level to the target-language modifier string.
    *
    * The returned string typically includes a trailing space (e.g., `"public "`)
    * or is empty for the default visibility.
    *
    * @param v the IR visibility level
    * @return the language-specific visibility modifier string
    */
  def visibility(v: Visibility): String

  // -- Type parameters --

  /**
    * Formats a list of type parameters with their bounds.
    *
    * Returns an empty string if the list is empty.
    *
    * @param tps the type parameter definitions
    * @param tm  the [[TypeMapping]] for formatting bound type names
    * @return the formatted type parameter list (e.g., `"<T extends Comparable<T>>"` or `"[A <: Ordered[A]]"`)
    */
  def typeParamList(tps: Vector[TypeParam], tm: TypeMapping): String

  // -- Expressions --

  /**
    * Formats a constructor/new-instance expression.
    *
    * @param typeName the type being instantiated
    * @param args     the formatted constructor arguments
    * @return the expression string (e.g., `"new Foo(x, y)"` or `"Foo(x, y)"`)
    */
  def newExpr(typeName: String, args: String): String

  /**
    * Formats a type cast expression.
    *
    * @param expr     the expression being cast
    * @param typeName the target type name
    * @return the cast expression (e.g., `"((Foo) expr)"` or `"expr.asInstanceOf[Foo]"`)
    */
  def castExpr(expr: String, typeName: String): String

  /**
    * Formats a ternary/conditional expression.
    *
    * @param cond the condition expression
    * @param t    the true-branch expression
    * @param f    the false-branch expression
    * @return the ternary expression string
    */
  def ternaryExpr(cond: String, t: String, f: String): String

  /**
    * Formats a lambda/anonymous function expression.
    *
    * @param params the formatted parameter names
    * @param body   the lambda body expression
    * @return the lambda expression string (e.g., `"(x) -> x + 1"` or `"x => x + 1"`)
    */
  def lambdaExpr(params: String, body: String): String

  /**
    * The null literal in this language.
    *
    * @return the null literal (e.g., `"null"`)
    */
  def nullLiteral: String

  /**
    * The this/self reference in this language.
    *
    * @return the self-reference keyword (e.g., `"this"`)
    */
  def thisLiteral: String

  /**
    * The super reference in this language.
    *
    * @return the super keyword (e.g., `"super"`)
    */
  def superLiteral: String

  // -- Statements --

  /**
    * Formats a return statement with an expression.
    *
    * @param expr the expression to return
    * @return the return statement string (e.g., `"return expr;"` in Java, or just `"expr"` in Scala)
    */
  def returnStmt(expr: String): String

  /**
    * Formats a void/unit return statement.
    *
    * @return the void return string (e.g., `"return;"` in Java, `"()"` in Scala)
    */
  def returnVoid: String

  /**
    * Formats a local variable declaration.
    *
    * @param mutable  whether the variable is mutable
    * @param typeName the variable's type name
    * @param name     the variable name
    * @param init     an optional initializer expression
    * @return the let/var declaration string
    */
  def letStmt(mutable: Boolean, typeName: String, name: String, init: Option[String]): String

  /**
    * Formats an assignment statement.
    *
    * @param target the assignment target expression
    * @param value  the value expression
    * @return the assignment string (e.g., `"x = 42;"`)
    */
  def assignStmt(target: String, value: String): String

  /**
    * Formats a throw statement.
    *
    * @param expr the exception expression to throw
    * @return the throw statement string (e.g., `"throw expr;"`)
    */
  def throwStmt(expr: String): String

  /**
    * Formats the header of a for-each loop.
    *
    * @param varName  the loop variable name
    * @param typeName the loop variable's type name
    * @param iterExpr the iterable expression
    * @return the for-each header string (e.g., `"for (int x : list)"` or `"for (x <- list)"`)
    */
  def forEachHeader(varName: String, typeName: String, iterExpr: String): String

  // -- Documentation --

  /**
    * Renders structured documentation (`Doc`) to the language-specific comment format.
    *
    * The default implementation produces Javadoc/Scaladoc style with `@param`, `@return`,
    * `@throws`, and `{{{ }}}` example blocks. Override for languages with different
    * documentation conventions.
    *
    * @param doc the structured documentation to render
    * @return the formatted documentation comment string, including delimiters
    */
  def renderDoc(doc: Doc): String = {
    val lines = Vector.newBuilder[String]
    lines += doc.summary
    doc.description.foreach { d =>
      lines += ""
      lines += d
    }
    if (doc.params.nonEmpty) {
      lines += ""
      doc.params.foreach { case (name, desc) => lines += s"@param $name $desc" }
    }
    doc.returns.foreach(r => lines += s"@return $r")
    doc.throws.foreach { case (ex, desc) => lines += s"@throws $ex $desc" }
    doc.tags.foreach { case (tag, value) => lines += s"@$tag $value" }
    if (doc.examples.nonEmpty) {
      lines += ""
      doc.examples.foreach { ex =>
        lines += "{{{"
        lines += ex
        lines += "}}}"
      }
    }
    val content = lines.result()
    if (content.size == 1) s"/** ${content.head} */"
    else ("/**" +: content.map(l => s" * $l") :+ " */").mkString("\n")
  }

  // -- Naming conventions --

  /**
    * Transforms a method name according to the target language's naming conventions.
    *
    * The default implementation returns the name unchanged. Override for languages
    * like Scala where getters should have their `get` prefix stripped and names
    * should follow camelCase conventions.
    *
    * @param name the original method name from the IR
    * @return the transformed method name
    */
  def transformMethodName(name: String): String = name

  /**
    * Transforms a field name according to the target language's naming conventions.
    *
    * The default implementation returns the name unchanged. Override for languages
    * that use camelCase field names.
    *
    * @param name the original field name from the IR
    * @return the transformed field name
    */
  def transformFieldName(name: String): String = name

  // -- Body style --

  /**
    * Whether this language uses `=` style for single-expression function bodies.
    *
    * When `true`, a function with a single-line body is rendered as
    * `def x: Int = expr` (Scala style). When `false`, it uses a braced body
    * `int x() { return expr; }` (Java style).
    *
    * @return `true` for expression-style bodies, `false` for brace-style
    */
  def useFuncEqualsStyle: Boolean = false

  // -- Pattern matching --

  /**
    * Formats the header of a pattern match expression.
    *
    * @param expr the expression being matched
    * @return the match header string (e.g., `"expr match"` in Scala)
    */
  def matchHeader(expr: String): String

  /**
    * Formats a single match case header.
    *
    * @param pattern the pattern expression with optional guard
    * @return the case header string (e.g., `"case pattern =>"` in Scala)
    */
  def matchCaseHeader(pattern: String): String

  /**
    * Formats a type-test pattern.
    *
    * @param name     the binding variable name
    * @param typeName the type being tested against
    * @return the type-test pattern string (e.g., `"name: Type"` in Scala or
    *         `"name instanceof Type"` in Java)
    */
  def patternTypeTest(name: String, typeName: String): String

  /**
    * The wildcard/default pattern for this language.
    *
    * @return the wildcard pattern string (e.g., `"_"` in Scala, `"default"` in Java)
    */
  def patternWildcard: String

  /**
    * Formats a literal pattern.
    *
    * @param value the literal value as a string
    * @return the literal pattern string (typically the value as-is)
    */
  def patternLiteral(value: String): String

  /**
    * Whether this language supports native pattern matching syntax.
    *
    * When `true`, match statements are rendered using [[MatchBlock]].
    * When `false`, they are desugared into an if/else chain.
    *
    * @return `true` if native match is supported (e.g., Scala), `false` otherwise (e.g., Java)
    */
  def supportsNativeMatch: Boolean

  // -- Operators --

  /**
    * Converts an IR binary operator to its target-language symbol.
    *
    * @param op the binary operator
    * @return the operator symbol string (e.g., `"=="`, `"!="`, `"+"`)
    */
  def binOp(op: BinaryOp): String

  /**
    * Converts an IR unary operator to its target-language symbol.
    *
    * @param op the unary operator
    * @return the operator symbol string (e.g., `"!"`, `"-"`, `"~"`)
    */
  def unaryOp(op: UnaryOp): String
}
