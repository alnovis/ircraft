package io.alnovis.ircraft.core

import scala.compiletime.{ constValue, constValueTuple, erasedValue, summonInline }
import scala.deriving.Mirror

// ── FieldTypeOf - lightweight type -> FieldType mapping (for schema) ───

/** Maps a Scala field type to its FieldType descriptor. None for nested case classes. */
sealed trait FieldTypeOf[A]:
  def fieldType: Option[FieldType]

object FieldTypeOf:

  given FieldTypeOf[String] with
    def fieldType: Option[FieldType] = Some(FieldType.StringField)

  given FieldTypeOf[Int] with
    def fieldType: Option[FieldType] = Some(FieldType.IntField)

  given FieldTypeOf[Long] with
    def fieldType: Option[FieldType] = Some(FieldType.LongField)

  given FieldTypeOf[Boolean] with
    def fieldType: Option[FieldType] = Some(FieldType.BoolField)

  given FieldTypeOf[List[String]] with
    def fieldType: Option[FieldType] = Some(FieldType.StringListField)

  given nested[A](using IrcraftSchema[A]): FieldTypeOf[A] with
    def fieldType: Option[FieldType] = None

// ── FieldCodec - full encode/decode for data codec ────────────────────

/** Encodes/decodes a single field type to/from IR attributes and regions. */
sealed trait FieldCodec[A]:
  def encode(key: String, value: A, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region])
  def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): A

object FieldCodec:

  given FieldCodec[String] with

    def encode(
      key: String,
      value: String,
      attrs: AttributeMap,
      regions: Vector[Region]
    ): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.StringAttr(key, value), regions)

    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): String =
      attrs.getString(key).getOrElse(throw IllegalArgumentException(s"Missing string field '$key'"))

  given FieldCodec[Int] with

    def encode(key: String, value: Int, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.IntAttr(key, value), regions)

    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): Int =
      attrs.getInt(key).getOrElse(throw IllegalArgumentException(s"Missing int field '$key'"))

  given FieldCodec[Long] with

    def encode(key: String, value: Long, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.LongAttr(key, value), regions)

    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): Long =
      attrs.getLong(key).getOrElse(throw IllegalArgumentException(s"Missing long field '$key'"))

  given FieldCodec[Boolean] with

    def encode(
      key: String,
      value: Boolean,
      attrs: AttributeMap,
      regions: Vector[Region]
    ): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.BoolAttr(key, value), regions)

    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): Boolean =
      attrs.getBool(key).getOrElse(throw IllegalArgumentException(s"Missing bool field '$key'"))

  given FieldCodec[List[String]] with

    def encode(
      key: String,
      value: List[String],
      attrs: AttributeMap,
      regions: Vector[Region]
    ): (AttributeMap, Vector[Region]) =
      (attrs + Attribute.StringListAttr(key, value), regions)

    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): List[String] =
      attrs.getStringList(key).getOrElse(throw IllegalArgumentException(s"Missing string list field '$key'"))

  /** Nested case class - encode/decode via its IrcraftCodec. */
  given nested[A](using codec: IrcraftCodec[A]): FieldCodec[A] with

    def encode(key: String, value: A, attrs: AttributeMap, regions: Vector[Region]): (AttributeMap, Vector[Region]) =
      (attrs, regions :+ Region(key, Vector(codec.encode(value))))

    def decode(key: String, attrs: AttributeMap, regions: Vector[Region]): A =
      val region = regions
        .find(_.name == key)
        .getOrElse(
          throw IllegalArgumentException(s"Missing region '$key' for nested type")
        )
      val op = region.operations.headOption.getOrElse(
        throw IllegalArgumentException(s"Region '$key' is empty, expected nested op")
      )
      codec.decode(op.asInstanceOf[GenericOp])

// ── IrcraftSchema - type structure metadata (for codegen) ─────────────

/**
  * Compile-time schema derivation for case classes - structure only, no data.
  *
  * {{{
  * case class Person(name: String, age: Int) derives IrcraftSchema
  *
  * // Schema metadata
  * IrcraftSchema[Person].opName        // "person"
  * IrcraftSchema[Person].fieldSchemas  // Vector(("name", StringField), ("age", IntField))
  *
  * // Schema-only IR for codegen pipelines
  * val module = IrcraftSchema.module("myapp", IrcraftSchema[Person])
  * }}}
  */
trait IrcraftSchema[A]:
  /** Operation name (lowercase class name). */
  def opName: String

  /** Primitive field metadata for dialect generation. */
  def fieldSchemas: Vector[(String, FieldType)]

  /** Region slots for nested case class fields. */
  def childSlots: Vector[String]

  /** Create an extractor for pattern matching ops of this schema type. */
  def extractor: GenericDialect.OpExtractor =
    GenericDialect.OpExtractor(NodeKind(IrcraftSchema.DefaultNamespace, opName))

  /** Create a transform pass scoped to ops of this schema type. */
  def transformPass(passName: String, passDescription: String = "")(
    pf: PartialFunction[GenericOp, GenericOp]
  ): Pass =
    val expectedKind = NodeKind(IrcraftSchema.DefaultNamespace, opName)
    val desc =
      if passDescription.nonEmpty then passDescription
      else s"Transform pass '$passName' for derived type '$opName'"
    new Pass:
      val name: String        = passName
      val description: String = desc
      def run(module: IrModule, context: PassContext): PassResult =
        import Traversal.transform
        val transformed = module.transform:
          case g: GenericOp if g.kind == expectedKind => pf.lift(g).getOrElse(g)
        PassResult(transformed)

object IrcraftSchema:

  val DefaultNamespace: String = "derived"

  inline def apply[A](using schema: IrcraftSchema[A]): IrcraftSchema[A] = schema

  /** Derive schema from case class via Mirror - extracts structure only. */
  inline def derived[A <: Product](using m: Mirror.ProductOf[A]): IrcraftSchema[A] =
    val nameStr    = constValue[m.MirroredLabel].toLowerCase
    val labels     = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    val fieldTypes = summonFieldTypes[m.MirroredElemTypes]
    buildSchema(nameStr, labels, fieldTypes)

  private def buildSchema[A](
    nameStr: String,
    labels: List[String],
    fieldTypes: List[FieldTypeOf[?]]
  ): IrcraftSchema[A] =
    val pairs = labels.zip(fieldTypes)
    val schemas = pairs.collect {
      case (name, ft) if ft.fieldType.isDefined => (name, ft.fieldType.get)
    }.toVector
    val slots = pairs.collect {
      case (name, ft) if ft.fieldType.isEmpty => name
    }.toVector
    new IrcraftSchema[A]:
      def opName: String                            = nameStr
      def fieldSchemas: Vector[(String, FieldType)] = schemas
      def childSlots: Vector[String]                = slots

  /** Create a GenericDialect from multiple schemas. */
  def dialect(namespace: String, schemas: IrcraftSchema[?]*): GenericDialect =
    val builder = GenericDialect.Builder(namespace)
    for s <- schemas do
      if s.childSlots.isEmpty then builder.leaf(s.opName, s.fieldSchemas*)
      else builder.container(s.opName, s.fieldSchemas*)(s.childSlots*)
    builder.build(s"Derived dialect: $namespace")

  /** Create a IrModule from type schemas (structure only, no data). */
  def module(namespace: String, schemas: IrcraftSchema[?]*): IrModule =
    val ops = schemas.map(schemaToOp(_, namespace)).toVector
    IrModule(namespace, ops)

  private def schemaToOp(s: IrcraftSchema[?], namespace: String): GenericOp =
    val fieldAttrs = s.fieldSchemas.map { (name, ft) =>
      Attribute.StringAttr(name, ft.toString)
    }
    val childRegions = s.childSlots.map(slot => Region(slot, Vector.empty))
    GenericOp(
      kind = NodeKind(namespace, s.opName),
      attributes = AttributeMap(fieldAttrs*),
      regions = childRegions
    )

  private inline def summonFieldTypes[T <: Tuple]: List[FieldTypeOf[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[FieldTypeOf[h]] :: summonFieldTypes[t]

// ── IrcraftCodec - data encode/decode (auto-derived from Schema) ──────

/**
  * Encode/decode case class instances to/from GenericOp.
  *
  * Auto-derived when `IrcraftSchema[A]` is available - no explicit `derives` needed:
  * {{{
  * case class Person(name: String, age: Int) derives IrcraftSchema
  *
  * // Codec is auto-available:
  * val op = IrcraftCodec[Person].encode(Person("John", 30))
  * val person = IrcraftCodec[Person].decode(op)
  *
  * // Or via extension methods:
  * import IrcraftCodec.*
  * val op = Person("John", 30).toOp
  * val person = op.to[Person]
  * }}}
  */
trait IrcraftCodec[A]:
  def schema: IrcraftSchema[A]
  def encode(value: A): GenericOp
  def decode(op: GenericOp): A

object IrcraftCodec:

  inline def apply[A](using codec: IrcraftCodec[A]): IrcraftCodec[A] = codec

  /** Auto-derive codec from IrcraftSchema + Mirror. */
  inline given derived[A <: Product](using s: IrcraftSchema[A], m: Mirror.ProductOf[A]): IrcraftCodec[A] =
    val labels = constValueTuple[m.MirroredElemLabels].toList.asInstanceOf[List[String]]
    val codecs = summonFieldCodecs[m.MirroredElemTypes]
    buildCodec[A](s, labels, codecs, m)

  private def buildCodec[A](
    s: IrcraftSchema[A],
    labels: List[String],
    codecs: List[FieldCodec[?]],
    m: Mirror.ProductOf[A]
  ): IrcraftCodec[A] =
    val fieldPairs = labels.zip(codecs)
    new IrcraftCodec[A]:
      def schema: IrcraftSchema[A] = s

      def encode(value: A): GenericOp =
        val product = value.asInstanceOf[Product]
        val (attrs, regs) = fieldPairs.zipWithIndex.foldLeft((AttributeMap.empty, Vector.empty[Region])):
          case ((attrs, regs), ((label, codec), idx)) =>
            codec.asInstanceOf[FieldCodec[Any]].encode(label, product.productElement(idx), attrs, regs)
        GenericOp(kind = NodeKind(IrcraftSchema.DefaultNamespace, s.opName), attributes = attrs, regions = regs)

      def decode(op: GenericOp): A =
        val values = fieldPairs.map { (label, codec) =>
          codec.asInstanceOf[FieldCodec[Any]].decode(label, op.attributes, op.regions)
        }
        m.fromTuple(Tuple.fromArray(values.toArray).asInstanceOf[m.MirroredElemTypes])

  private inline def summonFieldCodecs[T <: Tuple]: List[FieldCodec[?]] =
    inline erasedValue[T] match
      case _: EmptyTuple => Nil
      case _: (h *: t)   => summonInline[FieldCodec[h]] :: summonFieldCodecs[t]

  // ── Extension methods ───────────────────────────────────────────────

  extension [A](value: A)(using codec: IrcraftCodec[A])
    def toOp: GenericOp = codec.encode(value)

    def toOp(namespace: String): GenericOp =
      val op = codec.encode(value)
      op.copy(kind = NodeKind(namespace, codec.schema.opName))

  extension (op: GenericOp) def to[A](using codec: IrcraftCodec[A]): A = codec.decode(op)
