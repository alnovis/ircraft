package io.alnovis.ircraft.core

/** A generic operation that stores all data in AttributeMap and Region vectors.
  *
  * GenericOp is the runtime representation for operations defined via GenericDialect. It trades compile-time field type
  * safety for zero-boilerplate dialect creation.
  *
  * Analogies:
  *   - MLIR: generic `mlir::Operation` vs typed `arith::AddIOp`
  *   - Protobuf: `DynamicMessage` vs generated classes
  *   - JSON: `JsonNode` vs Jackson POJO
  */
case class GenericOp(
  kind: NodeKind,
  attributes: AttributeMap = AttributeMap.empty,
  regions: Vector[Region] = Vector.empty,
  span: Option[Span] = None
) extends Operation:

  // ── Field access ──────────────────────────────────────────────────────

  /** Get a String field by name. */
  def stringField(name: String): Option[String] = attributes.getString(name)

  /** Get an Int field by name. */
  def intField(name: String): Option[Int] = attributes.getInt(name)

  /** Get a Long field by name. */
  def longField(name: String): Option[Long] = attributes.getLong(name)

  /** Get a Bool field by name. */
  def boolField(name: String): Option[Boolean] = attributes.getBool(name)

  /** Get a String list field by name. */
  def stringListField(name: String): Option[List[String]] = attributes.getStringList(name)

  /** Get child operations from a named region. */
  def children(regionName: String): Vector[Operation] =
    region(regionName).map(_.operations).getOrElse(Vector.empty)

  // ── GreenNode ─────────────────────────────────────────────────────────

  lazy val contentHash: Int =
    ContentHash.combine(
      summon[ContentHashable[NodeKind]].contentHash(kind),
      attributes.contentHash,
      ContentHash.ofList(regions.toList)(using summon[ContentHashable[Region]])
    )

  lazy val estimatedSize: Int =
    1 + regions.map(_.operations.map(_.estimatedSize).sum).sum

  // ── mapChildren ───────────────────────────────────────────────────────

  override def mapChildren(f: Operation => Operation): GenericOp =
    if regions.isEmpty then this
    else copy(regions = regions.map(r => Region(r.name, r.operations.map(f))))
