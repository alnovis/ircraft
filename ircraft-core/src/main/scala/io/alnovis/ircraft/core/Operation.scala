package io.alnovis.ircraft.core

/**
  * An Operation is the fundamental unit of the IR, inspired by MLIR.
  *
  * Operations:
  *   - Belong to a Dialect (identified by kind)
  *   - Carry typed attributes (compile-time metadata)
  *   - Contain Regions (nested operation sequences)
  *   - Are immutable and content-addressable (GreenNode)
  *
  * Concrete operations in each dialect extend this trait. For example:
  *   - Proto dialect: MessageOp, FieldOp, EnumOp
  *   - Semantic dialect: ClassOp, InterfaceOp, MethodOp
  *
  * @see
  *   [[https://mlir.llvm.org/docs/LangRef/#operations MLIR Operations]]
  */
trait Operation extends GreenNode:

  /** The kind of this operation (dialect + operation name). */
  def kind: NodeKind

  /** Typed attributes attached to this operation. */
  def attributes: AttributeMap

  /** Regions (nested operation blocks) contained in this operation. */
  def regions: Vector[Region]

  /** Optional source location. */
  def span: Option[Span]

  /** Convenience: fully qualified operation name. */
  final def qualifiedName: String = kind.qualifiedName

  /** Convenience: dialect namespace. */
  final def dialectName: String = kind.dialect

  /** Convenience: operation name within dialect. */
  final def opName: String = kind.name

  /** Get a specific attribute by key. */
  final def attr(key: String): Option[Attribute] = attributes.get(key)

  /** Get a specific region by name. */
  final def region(name: String): Option[Region] = regions.find(_.name == name)

  /** All child operations across all regions. */
  final def children: Vector[Operation] = regions.flatMap(_.operations)

  /** Rebuild this operation with transformed children. Override in container operations. Leaf operations return this. */
  def mapChildren(f: Operation => Operation): Operation = this

  /** Extract typed operations from a named region. */
  final protected def regionOps[A <: Operation](name: String): Vector[A] =
    region(name).map(_.operations.collect { case a: A @unchecked => a }).getOrElse(Vector.empty)

object Operation:

  /** ContentHashable instance for Operation, usable by Region and others. */
  given operationHashable: ContentHashable[Operation] with
    def contentHash(a: Operation): Int = a.contentHash
