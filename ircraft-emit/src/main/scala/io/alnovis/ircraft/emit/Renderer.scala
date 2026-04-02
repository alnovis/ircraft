package io.alnovis.ircraft.emit

/** Pure function: CodeNode tree -> String. Cannot fail. */
object Renderer:

  def render(node: CodeNode, terminator: String = ";"): String =
    renderAt(node, 0, terminator)

  private def renderAt(node: CodeNode, level: Int, term: String): String = node match

    case CodeNode.File(header, imports, body) =>
      val sb = StringBuilder()
      sb.append(s"$header$term\n")
      if imports.nonEmpty then
        sb.append("\n")
        imports.sorted.foreach(i => sb.append(s"import $i$term\n"))
      body.foreach { n =>
        sb.append("\n")
        sb.append(renderAt(n, 0, term))
        sb.append("\n")
      }
      sb.result()

    case CodeNode.Line(text) =>
      ind(level, text)

    case CodeNode.Blank =>
      ""

    case CodeNode.Block(children) =>
      children.map(renderAt(_, level, term)).mkString("\n")

    case CodeNode.Braced(header, body) =>
      val bodyStr = body.map(renderAt(_, level + 1, term)).mkString("\n")
      if bodyStr.isEmpty then s"${ind(level, header)} {\n${ind(level, "}")}"
      else s"${ind(level, header)} {\n$bodyStr\n${ind(level, "}")}"

    case CodeNode.Func(signature, None) =>
      ind(level, s"$signature$term")

    case CodeNode.Func(signature, Some(body)) =>
      val bodyStr = body.map(renderAt(_, level + 1, term)).mkString("\n")
      if bodyStr.isEmpty then s"${ind(level, signature)} {\n${ind(level, "}")}"
      else s"${ind(level, signature)} {\n$bodyStr\n${ind(level, "}")}"

    case CodeNode.TypeBlock(signature, sections) =>
      val nonEmpty = sections.filter(_.nonEmpty)
      val bodyStr = nonEmpty.map { section =>
        section.map(renderAt(_, level + 1, term)).mkString("\n")
      }.mkString("\n\n")
      if bodyStr.isEmpty then s"${ind(level, signature)} {\n${ind(level, "}")}"
      else s"${ind(level, signature)} {\n$bodyStr\n${ind(level, "}")}"

    case CodeNode.IfElse(cond, thenBody, None) =>
      val thenStr = thenBody.map(renderAt(_, level + 1, term)).mkString("\n")
      s"${ind(level, s"if ($cond)")} {\n$thenStr\n${ind(level, "}")}"

    case CodeNode.IfElse(cond, thenBody, Some(elseBody)) =>
      val thenStr = thenBody.map(renderAt(_, level + 1, term)).mkString("\n")
      val elseStr = elseBody.map(renderAt(_, level + 1, term)).mkString("\n")
      s"${ind(level, s"if ($cond)")} {\n$thenStr\n${ind(level, "}")} else {\n$elseStr\n${ind(level, "}")}"

    case CodeNode.ForLoop(header, body) =>
      val bodyStr = body.map(renderAt(_, level + 1, term)).mkString("\n")
      s"${ind(level, header)} {\n$bodyStr\n${ind(level, "}")}"

    case CodeNode.TryCatch(tryBody, catches, finallyBody) =>
      val tryStr = tryBody.map(renderAt(_, level + 1, term)).mkString("\n")
      val sb = StringBuilder()
      sb.append(s"${ind(level, "try")} {\n$tryStr\n${ind(level, "}")}")
      catches.foreach { (catchHeader, catchBody) =>
        val cStr = catchBody.map(renderAt(_, level + 1, term)).mkString("\n")
        sb.append(s" catch ($catchHeader) {\n$cStr\n${ind(level, "}")}")
      }
      finallyBody.foreach { fb =>
        val fStr = fb.map(renderAt(_, level + 1, term)).mkString("\n")
        sb.append(s" finally {\n$fStr\n${ind(level, "}")}")
      }
      sb.result()

  private def ind(level: Int, s: String): String =
    "    " * level + s
