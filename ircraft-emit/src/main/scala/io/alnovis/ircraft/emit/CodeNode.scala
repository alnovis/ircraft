package io.alnovis.ircraft.emit

/**
  * Structured, language-agnostic representation of generated source code.
  *
  * A [[CodeNode]] tree captures the logical structure of emitted code (files, type blocks,
  * functions, control flow, etc.) without committing to any particular target language.
  * The tree is rendered to text by [[Renderer]], which applies indentation and bracing
  * conventions determined by a [[LanguageSyntax]].
  *
  * Leaf nodes such as [[CodeNode.Line]], [[CodeNode.Comment]], and [[CodeNode.Blank]]
  * represent single pieces of text, while composite nodes such as [[CodeNode.File]],
  * [[CodeNode.TypeBlock]], [[CodeNode.Func]], and [[CodeNode.IfElse]] model hierarchical
  * code constructs.
  *
  * @see [[Renderer]] for converting a [[CodeNode]] tree into a string
  * @see [[BaseEmitter]] for constructing [[CodeNode]] trees from semantic IR
  */
sealed trait CodeNode

/**
  * Companion object containing all [[CodeNode]] variants, organized into layout nodes
  * and structural concept nodes.
  */
object CodeNode {

  // -- Layout --

  /**
    * Represents a complete source file with a header, import declarations, and body nodes.
    *
    * The header typically contains a package declaration. Imports are sorted alphabetically
    * during rendering. Each body node is separated by a blank line.
    *
    * @param header  the file header text (e.g., a package declaration)
    * @param imports the set of import statements, rendered sorted after the header
    * @param body    the top-level declarations within the file
    *
    * @example {{{
    * CodeNode.File(
    *   header = "package com.example",
    *   imports = Vector("java.util.List", "java.util.Map"),
    *   body = Vector(CodeNode.Line("public class Foo {}"))
    * )
    * }}}
    */
  case class File(header: String, imports: Vector[String], body: Vector[CodeNode]) extends CodeNode

  /**
    * A group of sibling [[CodeNode]] elements rendered sequentially, separated by newlines.
    *
    * Unlike [[File]], a [[Block]] does not introduce any additional indentation or wrapping.
    * It is used to aggregate related nodes such as documentation comments followed by a
    * declaration.
    *
    * @param children the ordered sequence of child nodes
    */
  case class Block(children: Vector[CodeNode]) extends CodeNode

  /**
    * A single line of source code text.
    *
    * Rendered at the current indentation level. Does not include a trailing newline or
    * statement terminator -- those are handled by the [[Renderer]].
    *
    * @param text the raw source text for this line
    */
  case class Line(text: String) extends CodeNode

  /**
    * A header followed by a brace-delimited body.
    *
    * This is a general-purpose node for any construct that has a header string and a
    * braced body, such as anonymous blocks, namespace wrappers, or custom constructs
    * that do not fit the more specific node types.
    *
    * @param header the text preceding the opening brace
    * @param body   the child nodes inside the braces, each indented one level
    */
  case class Braced(header: String, body: Vector[CodeNode]) extends CodeNode

  /**
    * A blank line in the output. Rendered as an empty string with no indentation.
    */
  case object Blank extends CodeNode

  // -- Structural concepts --

  /**
    * A function or method declaration.
    *
    * When `body` is `None`, the function is rendered as an abstract declaration
    * (e.g., `void foo();` in Java or `def foo: Unit` in Scala). When `body` is
    * `Some`, the function is rendered with a braced body block.
    *
    * If [[LanguageSyntax.useFuncEqualsStyle]] is `true` and the body consists of a
    * single [[Line]], the emitter may render it in expression style (e.g., `def x: Int = expr`).
    *
    * @param signature the complete function signature text
    * @param body      the optional body statements; `None` for abstract declarations
    */
  case class Func(
    signature: String,
    body: Option[Vector[CodeNode]]
  ) extends CodeNode

  /**
    * A type declaration block (class, interface, trait, enum, object).
    *
    * The body is divided into sections separated by blank lines. Each section
    * typically groups related members (e.g., fields in one section, methods in another,
    * nested types in a third).
    *
    * @param signature the full type signature including visibility, keyword, name,
    *                  type parameters, and supertypes
    * @param sections  groups of member nodes, separated by blank lines in the output
    *
    * @example {{{
    * CodeNode.TypeBlock(
    *   signature = "public class Person",
    *   sections = Vector(
    *     Vector(CodeNode.Line("private final String name;")),
    *     Vector(CodeNode.Line("public String getName() { return name; }"))
    *   )
    * )
    * }}}
    */
  case class TypeBlock(
    signature: String,
    sections: Vector[Vector[CodeNode]]
  ) extends CodeNode

  /**
    * An if/else conditional block.
    *
    * When `elseBody` is `None`, only the `if` branch is rendered.
    * When present, the else branch is rendered with `} else {` style.
    *
    * @param cond     the condition expression text (without surrounding parentheses
    *                 in the model; parentheses are added by the renderer)
    * @param thenBody the statements for the true branch
    * @param elseBody the optional statements for the false branch
    */
  case class IfElse(
    cond: String,
    thenBody: Vector[CodeNode],
    elseBody: Option[Vector[CodeNode]]
  ) extends CodeNode

  /**
    * A for-loop construct.
    *
    * The header contains the full loop declaration (e.g., `for (int x : list)` in Java
    * or `for (x <- list)` in Scala). The body is rendered inside braces.
    *
    * @param header the for-loop header text
    * @param body   the loop body statements
    */
  case class ForLoop(header: String, body: Vector[CodeNode]) extends CodeNode

  /**
    * A while-loop construct.
    *
    * @param cond the loop condition expression text
    * @param body the loop body statements
    */
  case class WhileLoop(cond: String, body: Vector[CodeNode]) extends CodeNode

  /**
    * A switch/case block (C-family languages).
    *
    * Each case is a tuple of the pattern expression and the case body. An optional
    * default case is rendered at the end.
    *
    * @param expr    the expression being switched on
    * @param cases   the case branches as `(pattern, body)` pairs
    * @param default the optional default branch body
    *
    * @see [[MatchBlock]] for pattern-matching in languages like Scala
    */
  case class SwitchBlock(expr: String, cases: Vector[(String, Vector[CodeNode])], default: Option[Vector[CodeNode]])
      extends CodeNode

  /**
    * A pattern match block (Scala-style `expr match { ... }`).
    *
    * Each case is a tuple of the case header (e.g., `case x: Int =>`) and the
    * corresponding body. Unlike [[SwitchBlock]], there is no separate default --
    * a wildcard pattern is used instead.
    *
    * @param expr  the match expression header (e.g., `x match`)
    * @param cases the match cases as `(pattern, body)` pairs
    *
    * @see [[SwitchBlock]] for C-style switch blocks
    */
  case class MatchBlock(expr: String, cases: Vector[(String, Vector[CodeNode])]) extends CodeNode

  /**
    * A source code comment.
    *
    * Single-line comments are rendered with `//` prefix. Multi-line comments
    * (text containing `\n`) are rendered in block comment style.
    *
    * @param text the comment content; may contain newlines for multi-line comments
    */
  case class Comment(text: String) extends CodeNode

  /**
    * A try/catch/finally block.
    *
    * @param tryBody     the statements inside the `try` block
    * @param catches     the catch clauses as `(exceptionDecl, body)` pairs,
    *                    where `exceptionDecl` is e.g. `"Exception e"` in Java
    * @param finallyBody the optional `finally` block body
    *
    * @example {{{
    * CodeNode.TryCatch(
    *   tryBody = Vector(CodeNode.Line("doWork();")),
    *   catches = Vector(("Exception e", Vector(CodeNode.Line("log(e);")))),
    *   finallyBody = Some(Vector(CodeNode.Line("cleanup();")))
    * )
    * }}}
    */
  case class TryCatch(
    tryBody: Vector[CodeNode],
    catches: Vector[(String, Vector[CodeNode])],
    finallyBody: Option[Vector[CodeNode]]
  ) extends CodeNode
}
