package io.alnovis.ircraft.core

import scala.collection.mutable

/** Schema for a single operation in a generic dialect.
  *
  * @param name
  *   Operation name within the dialect
  * @param fields
  *   Expected field names and their types
  * @param childSlots
  *   Named region slots for container ops (empty for leaves)
  */
case class OpSchema(
  name: String,
  fields: Vector[(String, FieldType)],
  childSlots: Vector[String]
):
  def isContainer: Boolean = childSlots.nonEmpty
  def isLeaf: Boolean      = childSlots.isEmpty

/** A dialect defined entirely via a declarative DSL, producing GenericOp instances.
  *
  * Usage:
  * {{{
  * val ConfigDialect = GenericDialect("config"):
  *   leaf("entry", "key" -> StringField, "value" -> StringField)
  *   container("section", "name" -> StringField)(children = "entries")
  * }}}
  */
class GenericDialect private (
  val namespace: String,
  val description: String,
  schemas: Map[String, OpSchema]
) extends Dialect:

  // ── Dialect trait ────────────────────────────────────────────────────

  val operationKinds: Set[NodeKind] =
    schemas.keys.map(name => NodeKind(namespace, name)).toSet

  def verify(op: Operation): List[DiagnosticMessage] =
    if !owns(op) then
      List(DiagnosticMessage.error(s"Operation ${op.qualifiedName} does not belong to $namespace dialect"))
    else
      schemas.get(op.opName) match
        case None =>
          List(DiagnosticMessage.error(s"Unknown operation '${op.opName}' in dialect $namespace"))
        case Some(opSchema) =>
          val missingFields = opSchema.fields.collect:
            case (name, _) if !op.attributes.contains(name) => name
          if missingFields.nonEmpty then
            List(DiagnosticMessage.warning(
              s"Operation ${op.qualifiedName} missing fields: ${missingFields.mkString(", ")}"
            ))
          else Nil

  // ── Kind accessor ───────────────────────────────────────────────────

  /** Get the NodeKind for a named operation. */
  def kind(opName: String): NodeKind =
    require(schemas.contains(opName),
      s"Unknown operation '$opName' in dialect '$namespace'. Known: ${schemas.keys.mkString(", ")}")
    NodeKind(namespace, opName)

  /** Get the schema for a named operation. */
  def schema(opName: String): Option[OpSchema] = schemas.get(opName)

  // ── Factory ─────────────────────────────────────────────────────────

  /** Create a leaf operation. */
  def create(opName: String, fields: (String, Any)*): GenericOp =
    createOp(opName, fields, childRegions = Vector.empty)

  /** Create a container operation with child regions. */
  def createContainer(
    opName: String,
    fields: Seq[(String, Any)],
    children: (String, Vector[Operation])*
  ): GenericOp = createOp(opName, fields, childRegions = children.toVector)

  private def createOp(
    opName: String,
    fields: Seq[(String, Any)],
    childRegions: Vector[(String, Vector[Operation])]
  ): GenericOp =
    val opSchema = schemas.getOrElse(opName,
      throw IllegalArgumentException(
        s"Unknown operation '$opName' in dialect '$namespace'. Known: ${schemas.keys.mkString(", ")}"
      ))

    val attrs = fields.map: (key, value) =>
      value match
        case s: String     => Attribute.StringAttr(key, s)
        case i: Int        => Attribute.IntAttr(key, i)
        case l: Long       => Attribute.LongAttr(key, l)
        case b: Boolean    => Attribute.BoolAttr(key, b)
        case ss: List[?]   => Attribute.StringListAttr(key, ss.asInstanceOf[List[String]])
        case a: Attribute  => a
        case other =>
          throw IllegalArgumentException(
            s"Unsupported field value type for '$key': ${other.getClass.getSimpleName}"
          )

    val regions: Vector[Region] =
      if opSchema.isLeaf then Vector.empty
      else
        val childMap = childRegions.toMap
        opSchema.childSlots.map: slotName =>
          Region(slotName, childMap.getOrElse(slotName, Vector.empty))

    GenericOp(
      kind = NodeKind(namespace, opName),
      attributes = AttributeMap(attrs*),
      regions = regions
    )

object GenericDialect:

  /** Builder context for the DSL. */
  class Builder(val namespace: String):
    private val schemas = mutable.LinkedHashMap.empty[String, OpSchema]

    /** Declare a leaf operation (no children). */
    def leaf(name: String, fields: (String, FieldType)*): Unit =
      require(!schemas.contains(name), s"Duplicate operation name: '$name'")
      schemas(name) = OpSchema(name, fields.toVector, childSlots = Vector.empty)

    /** Declare a container operation with named child region slots. */
    def container(name: String, fields: (String, FieldType)*)(children: String*): Unit =
      require(!schemas.contains(name), s"Duplicate operation name: '$name'")
      require(children.nonEmpty, s"Container '$name' must have at least one child slot")
      schemas(name) = OpSchema(name, fields.toVector, childSlots = children.toVector)

    private[core] def build(description: String): GenericDialect =
      new GenericDialect(namespace, description, schemas.toMap)

  /** Create a GenericDialect using a builder DSL. */
  def apply(namespace: String, description: String = "")(configure: Builder ?=> Unit): GenericDialect =
    val builder = Builder(namespace)
    configure(using builder)
    builder.build(if description.isEmpty then s"Generic dialect: $namespace" else description)

  /** DSL entrypoints — available inside GenericDialect(...) { ... } block. */
  def leaf(name: String, fields: (String, FieldType)*)(using b: Builder): Unit =
    b.leaf(name, fields*)

  def container(name: String, fields: (String, FieldType)*)(children: String*)(using b: Builder): Unit =
    b.container(name, fields*)(children*)
