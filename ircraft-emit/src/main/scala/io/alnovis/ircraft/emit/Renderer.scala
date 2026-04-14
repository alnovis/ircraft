package io.alnovis.ircraft.emit

import cats.Eval
import cats.syntax.all._

/**
  * Renders a [[CodeNode]] tree into a formatted source code string.
  *
  * The renderer traverses the tree recursively, applying indentation (4 spaces per level)
  * and structural formatting (braces, blank-line separation, etc.). It uses `cats.Eval`
  * for stack-safe recursion on deeply nested trees.
  *
  * This object is stateless and thread-safe. All language-specific decisions (keywords,
  * terminators, etc.) are captured in the [[CodeNode]] tree itself, except for the
  * statement terminator which is passed as a parameter.
  *
  * @see [[CodeNode]] for the tree structure
  * @see [[BaseEmitter]] for constructing [[CodeNode]] trees from semantic IR
  */
object Renderer {

  /**
    * Renders a [[CodeNode]] tree to a formatted source code string.
    *
    * @param node       the root [[CodeNode]] to render
    * @param terminator the statement terminator to append to certain constructs
    *                   (e.g., `";"` for Java, `""` for Scala). Defaults to `";"`.
    * @return the rendered source code as a string
    *
    * @example {{{
    * val tree = CodeNode.File("package com.example", Vector.empty, Vector(
    *   CodeNode.Line("public class Foo {}")
    * ))
    * val source = Renderer.render(tree, ";")
    * }}}
    */
  def render(node: CodeNode, terminator: String = ";"): String =
    renderAt(node, 0, terminator).value

  private def renderAt(node: CodeNode, level: Int, term: String): Eval[String] = Eval.defer {
    node match {

      case CodeNode.File(header, imports, body) =>
        body.toList.traverse(n => renderAt(n, 0, term)).map { renderedBody =>
          val sb = new StringBuilder()
          sb.append(s"$header$term\n")
          if (imports.nonEmpty) {
            sb.append("\n")
            imports.sorted.foreach(i => sb.append(s"import $i$term\n"))
          }
          renderedBody.foreach { n =>
            sb.append("\n")
            sb.append(n)
            sb.append("\n")
          }
          sb.result()
        }

      case CodeNode.Line(text) =>
        Eval.now(ind(level, text))

      case CodeNode.Blank =>
        Eval.now("")

      case CodeNode.Block(children) =>
        children.toList.traverse(renderAt(_, level, term)).map(_.mkString("\n"))

      case CodeNode.Braced(header, body) =>
        body.toList.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          if (bodyStr.isEmpty) s"${ind(level, header)} {\n${ind(level, "}")}"
          else s"${ind(level, header)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.Func(signature, None) =>
        Eval.now(ind(level, s"$signature$term"))

      case CodeNode.Func(signature, Some(body)) =>
        body.toList.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          if (bodyStr.isEmpty) s"${ind(level, signature)} {\n${ind(level, "}")}"
          else s"${ind(level, signature)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.TypeBlock(signature, sections) =>
        val nonEmpty = sections.filter(_.nonEmpty)
        nonEmpty.toList
          .traverse { section =>
            section.toList.traverse(renderAt(_, level + 1, term)).map(_.mkString("\n"))
          }
          .map { renderedSections =>
            val bodyStr = renderedSections.mkString("\n\n")
            if (bodyStr.isEmpty) s"${ind(level, signature)} {\n${ind(level, "}")}"
            else s"${ind(level, signature)} {\n$bodyStr\n${ind(level, "}")}"
          }

      case CodeNode.IfElse(cond, thenBody, None) =>
        thenBody.toList.traverse(renderAt(_, level + 1, term)).map { renderedThen =>
          val thenStr = renderedThen.mkString("\n")
          s"${ind(level, s"if ($cond)")} {\n$thenStr\n${ind(level, "}")}"
        }

      case CodeNode.IfElse(cond, thenBody, Some(elseBody)) =>
        (thenBody.toList.traverse(renderAt(_, level + 1, term)), elseBody.toList.traverse(renderAt(_, level + 1, term)))
          .mapN { (renderedThen, renderedElse) =>
            val thenStr = renderedThen.mkString("\n")
            val elseStr = renderedElse.mkString("\n")
            s"${ind(level, s"if ($cond)")} {\n$thenStr\n${ind(level, "}")} else {\n$elseStr\n${ind(level, "}")}"
          }

      case CodeNode.ForLoop(header, body) =>
        body.toList.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          s"${ind(level, header)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.WhileLoop(cond, body) =>
        body.toList.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          s"${ind(level, s"while ($cond)")} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.MatchBlock(expr, cases) =>
        cases.toList
          .traverse {
            case (pattern, body) =>
              body.toList.traverse(renderAt(_, level + 2, term)).map { renderedBody =>
                val sb = new StringBuilder()
                sb.append(s"${ind(level + 1, pattern)}\n")
                renderedBody.foreach(n => sb.append(n + "\n"))
                sb.result()
              }
          }
          .map { renderedCases =>
            val sb = new StringBuilder()
            sb.append(s"${ind(level, expr)} {\n")
            renderedCases.foreach(sb.append)
            sb.append(ind(level, "}"))
            sb.result()
          }

      case CodeNode.SwitchBlock(expr, cases, default) =>
        val casesEval = cases.toList.traverse {
          case (pattern, body) =>
            body.toList.traverse(renderAt(_, level + 2, term)).map { renderedBody =>
              val sb = new StringBuilder()
              sb.append(s"${ind(level + 1, s"case $pattern:")}\n")
              renderedBody.foreach(n => sb.append(n + "\n"))
              sb.result()
            }
        }
        val defaultEval = default.map(_.toList).traverse { body =>
          body.traverse(renderAt(_, level + 2, term)).map { renderedBody =>
            val sb = new StringBuilder()
            sb.append(s"${ind(level + 1, "default:")}\n")
            renderedBody.foreach(n => sb.append(n + "\n"))
            sb.result()
          }
        }
        (casesEval, defaultEval).mapN { (renderedCases, renderedDefault) =>
          val sb = new StringBuilder()
          sb.append(s"${ind(level, s"switch ($expr)")} {\n")
          renderedCases.foreach(sb.append)
          renderedDefault.foreach(sb.append)
          sb.append(ind(level, "}"))
          sb.result()
        }

      case CodeNode.Comment(text) =>
        Eval.now {
          if (text.contains("\n")) {
            val lines = text.split("\n").toVector
            (ind(level, "/**") +: lines.map(l => ind(level, s" * $l")) :+ ind(level, " */")).mkString("\n")
          } else {
            ind(level, s"// $text")
          }
        }

      case CodeNode.TryCatch(tryBody, catches, finallyBody) =>
        val tryEval = tryBody.toList.traverse(renderAt(_, level + 1, term))
        val catchesEval = catches.toList.traverse {
          case (catchHeader, catchBody) =>
            catchBody.toList
              .traverse(renderAt(_, level + 1, term))
              .map(renderedBody => (catchHeader, renderedBody.mkString("\n")))
        }
        val finallyEval = finallyBody.map(_.toList).traverse(_.traverse(renderAt(_, level + 1, term)))
        (tryEval, catchesEval, finallyEval).mapN { (renderedTry, renderedCatches, renderedFinally) =>
          val tryStr = renderedTry.mkString("\n")
          val sb     = new StringBuilder()
          sb.append(s"${ind(level, "try")} {\n$tryStr\n${ind(level, "}")}")
          renderedCatches.foreach {
            case (catchHeader, cStr) =>
              sb.append(s" catch ($catchHeader) {\n$cStr\n${ind(level, "}")}")
          }
          renderedFinally.foreach { fb =>
            val fStr = fb.mkString("\n")
            sb.append(s" finally {\n$fStr\n${ind(level, "}")}")
          }
          sb.result()
        }
    }
  }

  private def ind(level: Int, s: String): String =
    "    " * level + s
}
