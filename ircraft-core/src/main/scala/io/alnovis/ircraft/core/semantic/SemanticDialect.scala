package io.alnovis.ircraft.core.semantic

import io.alnovis.ircraft.core.*

/**
  * Semantic Dialect: mid-level language-agnostic OOP constructs.
  *
  * This dialect contains abstractions common to Java, Kotlin, Scala, and most OOP languages. It does NOT include
  * language-specific features (checked exceptions, extension functions, data classes, etc.).
  *
  * Operations: FileOp, InterfaceOp, ClassOp, EnumClassOp, FieldDeclOp, MethodOp, ConstructorOp
  */
object SemanticDialect extends Dialect:

  val namespace: String   = "semantic"
  val description: String = "Mid-level language-agnostic type declarations"

  object Kinds:
    val File: NodeKind         = NodeKind(namespace, "file")
    val Interface: NodeKind    = NodeKind(namespace, "interface")
    val Class: NodeKind        = NodeKind(namespace, "class")
    val EnumClass: NodeKind    = NodeKind(namespace, "enum_class")
    val FieldDecl: NodeKind    = NodeKind(namespace, "field_decl")
    val Method: NodeKind       = NodeKind(namespace, "method")
    val Constructor: NodeKind  = NodeKind(namespace, "constructor")
    val EnumConstant: NodeKind = NodeKind(namespace, "enum_constant")

  val operationKinds: Set[NodeKind] = Set(
    Kinds.File,
    Kinds.Interface,
    Kinds.Class,
    Kinds.EnumClass,
    Kinds.FieldDecl,
    Kinds.Method,
    Kinds.Constructor,
    Kinds.EnumConstant
  )

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to semantic dialect"))
    else Nil
