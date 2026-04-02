package io.alnovis.ircraft.emit

/** Structured representation of generated code. Language-agnostic tree. */
sealed trait CodeNode

object CodeNode:
  // -- Layout --
  case class File(header: String, imports: Vector[String], body: Vector[CodeNode]) extends CodeNode
  case class Block(children: Vector[CodeNode]) extends CodeNode
  case class Line(text: String) extends CodeNode
  case class Braced(header: String, body: Vector[CodeNode]) extends CodeNode
  case object Blank extends CodeNode

  // -- Structural concepts --
  case class Func(
    signature: String,
    body: Option[Vector[CodeNode]]
  ) extends CodeNode

  case class TypeBlock(
    signature: String,
    sections: Vector[Vector[CodeNode]]
  ) extends CodeNode

  case class IfElse(
    cond: String,
    thenBody: Vector[CodeNode],
    elseBody: Option[Vector[CodeNode]]
  ) extends CodeNode

  case class ForLoop(header: String, body: Vector[CodeNode]) extends CodeNode
  case class WhileLoop(cond: String, body: Vector[CodeNode]) extends CodeNode
  case class SwitchBlock(expr: String, cases: Vector[(String, Vector[CodeNode])], default: Option[Vector[CodeNode]]) extends CodeNode
  case class Comment(text: String) extends CodeNode

  case class TryCatch(
    tryBody: Vector[CodeNode],
    catches: Vector[(String, Vector[CodeNode])],
    finallyBody: Option[Vector[CodeNode]]
  ) extends CodeNode
