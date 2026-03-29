package io.alnovis.ircraft.core

/**
  * Top-level container for an IR program.
  *
  * A IrModule is a special Operation that holds all top-level operations in a single region. It serves as the root of the
  * IR tree and the input/output of Pass transformations.
  */
case class IrModule(
  name: String,
  topLevel: Vector[Operation],
  attributes: AttributeMap = AttributeMap.empty,
  span: Option[Span] = None
) extends Operation:

  val kind: NodeKind = NodeKind("builtin", "module")

  val regions: Vector[Region] = Vector(Region("body", topLevel))

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      attributes.contentHash,
      ContentHash.ofList(topLevel.toList)(using Operation.operationHashable)
    )

  lazy val estimatedSize: Int = topLevel.map(_.estimatedSize).sum

  override def mapChildren(f: Operation => Operation): IrModule =
    copy(topLevel = topLevel.map(f))

  /** Find all operations of a specific type in the module. */
  def collect[A <: Operation](pf: PartialFunction[Operation, A]): Vector[A] =
    def go(ops: Vector[Operation]): Vector[A] =
      ops.flatMap: op =>
        val self   = pf.lift(op).toVector
        val nested = go(op.children)
        self ++ nested
    go(topLevel)

object IrModule:
  def empty(name: String): IrModule = IrModule(name, Vector.empty)
