package io.alnovis.ircraft.core

/**
  * A generic operation that stores all data in AttributeMap and Region vectors.
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

  // ── Predicates ──────────────────────────────────────────────────────

  /** Check if this op has the given operation name (ignoring dialect). */
  def is(opName: String): Boolean = kind.name == opName

  /** Check if this op belongs to the given dialect. */
  def isOf(dialect: Dialect): Boolean = kind.dialect == dialect.namespace

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

  // ── Immutable updates ──────────────────────────────────────────────

  /** Return a copy with a single field added or replaced. */
  def withField(name: String, value: Any): GenericOp =
    copy(attributes = attributes + GenericOp.toAttribute(name, value))

  /** Return a copy with multiple fields added or replaced. */
  def withFields(fields: (String, Any)*): GenericOp =
    val newAttrs = fields.foldLeft(attributes)((acc, kv) => acc + GenericOp.toAttribute(kv._1, kv._2))
    copy(attributes = newAttrs)

  /** Return a copy with the named field removed. */
  def without(fieldName: String): GenericOp =
    copy(attributes = attributes - fieldName)

  /** Return a copy with the named region replaced or added. */
  def withRegion(name: String, ops: Vector[Operation]): GenericOp =
    val newRegion = Region(name, ops)
    val idx       = regions.indexWhere(_.name == name)
    if idx >= 0 then copy(regions = regions.updated(idx, newRegion))
    else copy(regions = regions :+ newRegion)

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

object GenericOp:

  /** Convert a Scala value to an Attribute. Used by withField and GenericDialect factories. */
  private[core] def toAttribute(key: String, value: Any): Attribute =
    value match
      case s: String    => Attribute.StringAttr(key, s)
      case i: Int       => Attribute.IntAttr(key, i)
      case l: Long      => Attribute.LongAttr(key, l)
      case b: Boolean   => Attribute.BoolAttr(key, b)
      case ss: List[?]  => Attribute.StringListAttr(key, ss.asInstanceOf[List[String]])
      case a: Attribute => a
      case other =>
        throw IllegalArgumentException(
          s"Unsupported field value type for '$key': ${other.getClass.getSimpleName}"
        )
