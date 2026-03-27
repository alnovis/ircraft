package io.alnovis.ircraft.core

import scala.compiletime.{constValue, constValueTuple, erasedValue, summonInline}
import scala.deriving.Mirror

/**
  * Describes how a single field type maps to/from IR attributes and regions.
  *
  * Primitive types (String, Int, etc.) are stored as attributes. Nested case classes (with `derives IrcraftSchema`) are
  * stored as child operations in named regions.
  */
sealed trait FieldCodec[A]:
  /** FieldType for dialect schema generation. None for nested types (they use regions). */
  def fieldType: Option[FieldType]

  /** Encode a field value into attributes and regions. */
  def encode(key: String, value: A, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region])

  /** Decode a field value from attributes and regions. */
  def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): A

object FieldCodec:

  given FieldCodec[String] with
    def fieldType: Option[FieldType] = Some(FieldType.StringField)
    def encode(key: String, value: String, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.StringAttr(key, value), regions)
    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): String =
      attrs.getString(key).getOrElse(throw IllegalArgumentException(s"Missing string field '$key'"))

  given FieldCodec[Int] with
    def fieldType: Option[FieldType] = Some(FieldType.IntField)
    def encode(key: String, value: Int, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.IntAttr(key, value), regions)
    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): Int =
      attrs.getInt(key).getOrElse(throw IllegalArgumentException(s"Missing int field '$key'"))

  given FieldCodec[Long] with
    def fieldType: Option[FieldType] = Some(FieldType.LongField)
    def encode(key: String, value: Long, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.LongAttr(key, value), regions)
    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): Long =
      attrs.getLong(key).getOrElse(throw IllegalArgumentException(s"Missing long field '$key'"))

  given FieldCodec[Boolean] with
    def fieldType: Option[FieldType] = Some(FieldType.BoolField)
    def encode(key: String, value: Boolean, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.BoolAttr(key, value), regions)
    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): Boolean =
      attrs.getBool(key).getOrElse(throw IllegalArgumentException(s"Missing bool field '$key'"))

  given FieldCodec[List[String]] with
    def fieldType: Option[FieldType] = Some(FieldType.StringListField)
    def encode(key: String, value: List[String], attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.StringListAttr(key, value), regions)
    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): List[String] =
      attrs.getStringList(key).getOrElse(throw IllegalArgumentException(s"Missing string list field '$key'"))

  /** Nested case class — stored as a single child GenericOp in a named region. */
  given nested[A](using schema: IrcraftSchema[A]): FieldCodec[A] with
    def fieldType: Option[FieldType] = None
    def encode(key: String, value: A, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs, regions :+ Region(key, Vector(schema.toOp(value))))
    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): A =
      val region = regions.find(_.name == key).getOrElse(
        throw IllegalArgumentException(s"Missing region '$key' for nested type")
      )
      val op = region.operations.headOption.getOrElse(
        throw IllegalArgumentException(s"Region '$key' is empty, expected nested op")
      )
      schema.fromOp(op.asInstanceOf[GenericOp])

/**
  * Compile-time schema derivation for case classes.
  *
  * Enables automatic conversion between Scala case classes and IRCraft GenericOp operations:
  * {{{
  * case class Person(name: String, age: Int) derives IrcraftSchema
  *
  * val op = Person("John", 30).toOp       // GenericOp
  * val person = op.to[Person]              // Person("John", 30)
  * }}}
  *
  * Supported field types: String, Int, Long, Boolean, List[String], and nested case classes with `derives IrcraftSchema`.
  */
trait IrcraftSchema[A]:
  /** Operation name (lowercase class name). */
  def opName: String

  /** Primitive field metadata for dialect generation. */
  def fieldSchemas: Vector[(String, FieldType)]

  /** Region slots for nested case class fields. */
  def childSlots: Vector[String]

  /** Encode a value to GenericOp. */
  def toOp(value: A): GenericOp

  /** Decode a GenericOp back to a value. */
  def fromOp(op: GenericOp): A

object IrcraftSchema:

  /** Default namespace for derived operations. */
  val DefaultNamespace: String = "derived"

  /** Summon an existing IrcraftSchema instance. */
  inline def apply[A](using schema: IrcraftSchema[A]): IrcraftSchema[A] = schema

  /** Derive an IrcraftSchema for a case class via Scala 3 Mirror. Zero external dependencies. */
  inline def derived[A <: Product](using m: Mirror.ProductOf[A]): IrcraftSchema[A] =
    val nameStr = constValue[m.MirroredLabel].toLowerCase
    val labels  = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    val codecs  = summonCodecs[m.MirroredElemTypes]
    buildSchema[A](nameStr, labels, codecs, m)

  /** Build the schema instance (non-inline to avoid anonymous class duplication). */
  private def buildSchema[A](
    nameStr: String,
    labels: List[String],
    codecs: List[FieldCodec[?]],
    m: Mirror.ProductOf[A]
  ): IrcraftSchema[A] =
    val fieldPairs = labels.zip(codecs)

    val schemas = fieldPairs.collect {
      case (name, codec) if codec.fieldType.isDefined => (name, codec.fieldType.get)
    }.toVector

    val slots = fieldPairs.collect {
      case (name, codec) if codec.fieldType.isEmpty => name
    }.toVector

    new IrcraftSchema[A]:
      def opName: String                             = nameStr
      def fieldSchemas: Vector[(String, FieldType)]   = schemas
      def childSlots: Vector[String]                  = slots

      def toOp(value: A): GenericOp =
        val product = value.asInstanceOf[Product]
        var attrs   = AttributeMap.empty
        var regs    = Vector.empty[Region]
        var i       = 0
        for (_, codec) <- fieldPairs do
          val c          = codec.asInstanceOf[FieldCodec[Any]]
          val fieldValue = product.productElement(i)
          val (a, r)     = c.encode(labels(i), fieldValue, attrs, regs)
          attrs = a
          regs = r
          i += 1
        GenericOp(kind = NodeKind(DefaultNamespace, nameStr), attributes = attrs, regions = regs)

      def fromOp(op: GenericOp): A =
        val values = fieldPairs.map { (label, codec) =>
          codec.asInstanceOf[FieldCodec[Any]].decode(label, op.attributes, op.regions)
        }
        m.fromTuple(Tuple.fromArray(values.toArray).asInstanceOf[m.MirroredElemTypes])

  /** Create a GenericDialect from multiple IrcraftSchema instances. */
  def dialect(namespace: String, schemas: IrcraftSchema[?]*): GenericDialect =
    val builder = GenericDialect.Builder(namespace)
    for s <- schemas do
      if s.childSlots.isEmpty then builder.leaf(s.opName, s.fieldSchemas*)
      else builder.container(s.opName, s.fieldSchemas*)(s.childSlots*)
    builder.build(s"Derived dialect: $namespace")

  // ── Inline helpers ──────────────────────────────────────────────────

  private inline def summonCodecs[T <: Tuple]: List[FieldCodec[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[FieldCodec[h]] :: summonCodecs[t]

  // ── Extension methods ───────────────────────────────────────────────

  /** Encode a case class instance to GenericOp. */
  extension [A](value: A)(using schema: IrcraftSchema[A])
    def toOp: GenericOp = schema.toOp(value)
    def toOp(namespace: String): GenericOp =
      val op = schema.toOp(value)
      op.copy(kind = NodeKind(namespace, schema.opName))

  /** Decode a GenericOp to a case class instance. */
  extension (op: GenericOp)
    def to[A](using schema: IrcraftSchema[A]): A = schema.fromOp(op)
