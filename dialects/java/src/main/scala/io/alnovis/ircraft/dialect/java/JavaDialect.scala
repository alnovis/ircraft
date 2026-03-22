package io.alnovis.ircraft.dialect.java

import io.alnovis.ircraft.core.*

/** Java Code Dialect: low-level Java-specific code operations ready for emission.
  *
  * Operations: JavaFileOp, JavaClassOp, JavaInterfaceOp, JavaEnumOp, JavaMethodOp, JavaAnnotationOp
  */
object JavaDialect extends Dialect:

  val namespace: String   = "java.code"
  val description: String = "Low-level Java code operations ready for emission"

  object Kinds:
    val File: NodeKind       = NodeKind(namespace, "file")
    val Class: NodeKind      = NodeKind(namespace, "class")
    val Interface: NodeKind  = NodeKind(namespace, "interface")
    val Enum: NodeKind       = NodeKind(namespace, "enum")
    val Method: NodeKind     = NodeKind(namespace, "method")
    val Field: NodeKind      = NodeKind(namespace, "field")
    val Annotation: NodeKind = NodeKind(namespace, "annotation")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.File, Kinds.Class, Kinds.Interface, Kinds.Enum,
    Kinds.Method, Kinds.Field, Kinds.Annotation,
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to java.code dialect"))
    else Nil
