package io.alnovis.ircraft.core

/** Source location for an IR node.
  *
  * Used for diagnostics, debugging, and future LSP integration.
  */
case class Span(
    file: String,
    line: Int,
    column: Int,
    length: Int,
):
  def endColumn: Int = column + length

  override def toString: String = s"$file:$line:$column"

object Span:
  val unknown: Span = Span("<unknown>", 0, 0, 0)

  given ContentHashable[Span] with
    def contentHash(a: Span): Int =
      ContentHash.combine(
        ContentHash.ofString(a.file),
        a.line,
        a.column,
        a.length,
      )
