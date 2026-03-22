package io.alnovis.ircraft.core

/** A named container for a sequence of operations (MLIR concept).
  *
  * Regions allow operations to contain nested operations. For example, a ClassOp has regions for fields, constructors,
  * and methods.
  *
  * @param name
  *   region name (e.g., "fields", "methods", "body")
  * @param operations
  *   ordered sequence of operations in this region
  */
case class Region(name: String, operations: Vector[Operation]):
  def isEmpty: Boolean = operations.isEmpty
  def size: Int        = operations.size

object Region:
  def apply(name: String, ops: Operation*): Region = Region(name, ops.toVector)

  def empty(name: String): Region = Region(name, Vector.empty)

  given ContentHashable[Region] with
    def contentHash(a: Region): Int =
      val opsHash = ContentHash.ofList(a.operations.toList)(using Operation.operationHashable)
      ContentHash.combine(ContentHash.ofString(a.name), opsHash)
