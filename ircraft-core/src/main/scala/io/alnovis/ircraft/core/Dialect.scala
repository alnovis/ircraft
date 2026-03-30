package io.alnovis.ircraft.core

/**
  * A Dialect defines a set of operations at a specific abstraction level.
  *
  * Inspired by MLIR's dialect system:
  *   - Each dialect has a unique namespace
  *   - Dialects define their own operations and attributes
  *   - Lowerings transform between dialects (progressive lowering)
  *
  * @see
  *   [[https://mlir.llvm.org/docs/Dialects/ MLIR Dialects]]
  */
trait Dialect:

  /** Unique namespace for this dialect (e.g., "proto", "semantic", "java.code"). */
  def namespace: String

  /** Human-readable description. */
  def description: String

  /** Registered operation kinds in this dialect. */
  def operationKinds: Set[NodeKind]

  /** Validate that an operation is well-formed for this dialect.
    * Default: only checks ownership. Override for structural validation.
    */
  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to $namespace dialect"))
    else Nil

  /** Check if this dialect owns the given operation. */
  final def owns(op: Operation): Boolean = op.kind.dialect == namespace
