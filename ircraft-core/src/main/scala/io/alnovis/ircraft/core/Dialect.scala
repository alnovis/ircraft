package io.alnovis.ircraft.core

/** A Dialect defines a set of operations at a specific abstraction level.
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

  /** Validate that an operation is well-formed for this dialect. */
  def verify(op: Operation): List[DiagnosticMessage]

  /** Check if this dialect owns the given operation. */
  final def owns(op: Operation): Boolean = op.kind.dialect == namespace
