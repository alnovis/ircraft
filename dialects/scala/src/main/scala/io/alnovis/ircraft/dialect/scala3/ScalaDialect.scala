package io.alnovis.ircraft.dialect.scala3

import io.alnovis.ircraft.core.*

/** Scala 3 Code Dialect: Scala-specific code operations ready for emission. */
object ScalaDialect extends Dialect:

  val namespace: String   = "scala.code"
  val description: String = "Scala 3 code operations ready for emission"

  object Kinds:
    val File: NodeKind       = NodeKind(namespace, "file")
    val Class: NodeKind      = NodeKind(namespace, "class")
    val Trait: NodeKind      = NodeKind(namespace, "trait")
    val Enum: NodeKind       = NodeKind(namespace, "enum")
    val Method: NodeKind     = NodeKind(namespace, "method")
    val Field: NodeKind      = NodeKind(namespace, "field")
    val Annotation: NodeKind = NodeKind(namespace, "annotation")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.File,
    Kinds.Class,
    Kinds.Trait,
    Kinds.Enum,
    Kinds.Method,
    Kinds.Field,
    Kinds.Annotation
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to scala.code dialect"))
    else Nil
