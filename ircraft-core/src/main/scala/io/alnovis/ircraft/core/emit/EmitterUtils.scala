package io.alnovis.ircraft.core.emit

/** Shared formatting utilities for all language emitters. */
trait EmitterUtils:

  /** Indent a line by the given level (2 spaces per level). */
  def indent(level: Int, line: String): String =
    if line.isEmpty then line
    else "  " * level + line

  /** Indent all lines in a block. */
  def indentBlock(level: Int, text: String): String =
    text.linesIterator.map(indent(level, _)).mkString("\n")

  /**
    * Format a block with header and braces:
    * {{{
    * header {
    *   body
    * }
    * }}}
    */
  def block(header: String, level: Int)(body: => String): String =
    val open    = indent(level, s"$header {")
    val content = body
    val close   = indent(level, "}")
    if content.trim.isEmpty then s"$open\n$close"
    else s"$open\n$content\n$close"

  /** Join non-empty lines with separator. */
  def joinLines(lines: Seq[String], separator: String = "\n"): String =
    lines.filter(_.nonEmpty).mkString(separator)

  /** Wrap text in a documentation comment. */
  def wrapComment(style: CommentStyle, text: String, level: Int = 0): String =
    if text.isEmpty then ""
    else
      style match
        case CommentStyle.JavaDoc | CommentStyle.KDoc | CommentStyle.ScalaDoc =>
          val lines  = text.linesIterator.toList
          val prefix = indent(level, " * ")
          val open   = indent(level, "/**")
          val close  = indent(level, " */")
          (open +: lines.map(l => if l.isEmpty then indent(level, " *") else s"$prefix$l") :+ close).mkString("\n")
        case CommentStyle.LineComment =>
          text.linesIterator.map(l => indent(level, s"// $l")).mkString("\n")

  /** Wrap a list of items with comma separator, respecting line length. */
  def commaSeparated(items: Seq[String]): String = items.mkString(", ")

/** Documentation comment style. */
enum CommentStyle:
  case JavaDoc, KDoc, ScalaDoc, LineComment

object EmitterUtils extends EmitterUtils
