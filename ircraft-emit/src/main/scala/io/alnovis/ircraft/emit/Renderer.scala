package io.alnovis.ircraft.emit

import cats.Eval
import cats.syntax.all.*

/** Pure function: CodeNode tree -> String. Cannot fail. Stack-safe via Eval. */
object Renderer:

  def render(node: CodeNode, terminator: String = ";"): String =
    renderAt(node, 0, terminator).value

  private def renderAt(node: CodeNode, level: Int, term: String): Eval[String] = Eval.defer {
    node match

      case CodeNode.File(header, imports, body) =>
        body.traverse(n => renderAt(n, 0, term)).map { renderedBody =>
          val sb = StringBuilder()
          sb.append(s"$header$term\n")
          if imports.nonEmpty then
            sb.append("\n")
            imports.sorted.foreach(i => sb.append(s"import $i$term\n"))
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
        children.traverse(renderAt(_, level, term)).map(_.mkString("\n"))

      case CodeNode.Braced(header, body) =>
        body.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          if bodyStr.isEmpty then s"${ind(level, header)} {\n${ind(level, "}")}"
          else s"${ind(level, header)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.Func(signature, None) =>
        Eval.now(ind(level, s"$signature$term"))

      case CodeNode.Func(signature, Some(body)) =>
        body.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          if bodyStr.isEmpty then s"${ind(level, signature)} {\n${ind(level, "}")}"
          else s"${ind(level, signature)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.TypeBlock(signature, sections) =>
        val nonEmpty = sections.filter(_.nonEmpty)
        nonEmpty.traverse { section =>
          section.traverse(renderAt(_, level + 1, term)).map(_.mkString("\n"))
        }.map { renderedSections =>
          val bodyStr = renderedSections.mkString("\n\n")
          if bodyStr.isEmpty then s"${ind(level, signature)} {\n${ind(level, "}")}"
          else s"${ind(level, signature)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.IfElse(cond, thenBody, None) =>
        thenBody.traverse(renderAt(_, level + 1, term)).map { renderedThen =>
          val thenStr = renderedThen.mkString("\n")
          s"${ind(level, s"if ($cond)")} {\n$thenStr\n${ind(level, "}")}"
        }

      case CodeNode.IfElse(cond, thenBody, Some(elseBody)) =>
        (thenBody.traverse(renderAt(_, level + 1, term)),
         elseBody.traverse(renderAt(_, level + 1, term))).mapN { (renderedThen, renderedElse) =>
          val thenStr = renderedThen.mkString("\n")
          val elseStr = renderedElse.mkString("\n")
          s"${ind(level, s"if ($cond)")} {\n$thenStr\n${ind(level, "}")} else {\n$elseStr\n${ind(level, "}")}"
        }

      case CodeNode.ForLoop(header, body) =>
        body.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          s"${ind(level, header)} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.WhileLoop(cond, body) =>
        body.traverse(renderAt(_, level + 1, term)).map { renderedBody =>
          val bodyStr = renderedBody.mkString("\n")
          s"${ind(level, s"while ($cond)")} {\n$bodyStr\n${ind(level, "}")}"
        }

      case CodeNode.MatchBlock(expr, cases) =>
        cases.traverse { (pattern, body) =>
          body.traverse(renderAt(_, level + 2, term)).map { renderedBody =>
            val sb = StringBuilder()
            sb.append(s"${ind(level + 1, pattern)}\n")
            renderedBody.foreach(n => sb.append(n + "\n"))
            sb.result()
          }
        }.map { renderedCases =>
          val sb = StringBuilder()
          sb.append(s"${ind(level, expr)} {\n")
          renderedCases.foreach(sb.append)
          sb.append(ind(level, "}"))
          sb.result()
        }

      case CodeNode.SwitchBlock(expr, cases, default) =>
        val casesEval = cases.traverse { (pattern, body) =>
          body.traverse(renderAt(_, level + 2, term)).map { renderedBody =>
            val sb = StringBuilder()
            sb.append(s"${ind(level + 1, s"case $pattern:")}\n")
            renderedBody.foreach(n => sb.append(n + "\n"))
            sb.result()
          }
        }
        val defaultEval = default.traverse { body =>
          body.traverse(renderAt(_, level + 2, term)).map { renderedBody =>
            val sb = StringBuilder()
            sb.append(s"${ind(level + 1, "default:")}\n")
            renderedBody.foreach(n => sb.append(n + "\n"))
            sb.result()
          }
        }
        (casesEval, defaultEval).mapN { (renderedCases, renderedDefault) =>
          val sb = StringBuilder()
          sb.append(s"${ind(level, s"switch ($expr)")} {\n")
          renderedCases.foreach(sb.append)
          renderedDefault.foreach(sb.append)
          sb.append(ind(level, "}"))
          sb.result()
        }

      case CodeNode.Comment(text) =>
        Eval.now {
          if text.contains("\n") then
            val lines = text.split("\n").toVector
            (ind(level, "/**") +: lines.map(l => ind(level, s" * $l")) :+ ind(level, " */")).mkString("\n")
          else
            ind(level, s"// $text")
        }

      case CodeNode.TryCatch(tryBody, catches, finallyBody) =>
        val tryEval = tryBody.traverse(renderAt(_, level + 1, term))
        val catchesEval = catches.traverse { (catchHeader, catchBody) =>
          catchBody.traverse(renderAt(_, level + 1, term)).map(renderedBody =>
            (catchHeader, renderedBody.mkString("\n"))
          )
        }
        val finallyEval = finallyBody.traverse(_.traverse(renderAt(_, level + 1, term)))
        (tryEval, catchesEval, finallyEval).mapN { (renderedTry, renderedCatches, renderedFinally) =>
          val tryStr = renderedTry.mkString("\n")
          val sb = StringBuilder()
          sb.append(s"${ind(level, "try")} {\n$tryStr\n${ind(level, "}")}")
          renderedCatches.foreach { (catchHeader, cStr) =>
            sb.append(s" catch ($catchHeader) {\n$cStr\n${ind(level, "}")}")
          }
          renderedFinally.foreach { fb =>
            val fStr = fb.mkString("\n")
            sb.append(s" finally {\n$fStr\n${ind(level, "}")}")
          }
          sb.result()
        }
  }

  private def ind(level: Int, s: String): String =
    "    " * level + s
