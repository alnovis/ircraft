package io.alnovis.ircraft.core

/** Immutable, content-addressable collection of attributes. */
final case class AttributeMap private (private val attrs: Map[String, Attribute]):

  def get(key: String): Option[Attribute] = attrs.get(key)

  def getAs[A <: Attribute](key: String): Option[A] =
    attrs.get(key).collect { case a: A @unchecked => a }

  def getString(key: String): Option[String] =
    getAs[Attribute.StringAttr](key).map(_.value)

  def getInt(key: String): Option[Int] =
    getAs[Attribute.IntAttr](key).map(_.value)

  def getLong(key: String): Option[Long] =
    getAs[Attribute.LongAttr](key).map(_.value)

  def getBool(key: String): Option[Boolean] =
    getAs[Attribute.BoolAttr](key).map(_.value)

  def getStringList(key: String): Option[List[String]] =
    getAs[Attribute.StringListAttr](key).map(_.values)

  def contains(key: String): Boolean = attrs.contains(key)

  def isEmpty: Boolean = attrs.isEmpty

  def size: Int = attrs.size

  def keys: Set[String] = attrs.keySet

  def values: Iterable[Attribute] = attrs.values

  def toMap: Map[String, Attribute] = attrs

  def +(attr: Attribute): AttributeMap = AttributeMap(attrs + (attr.key -> attr))

  def ++(other: AttributeMap): AttributeMap = AttributeMap(attrs ++ other.attrs)

  def -(key: String): AttributeMap = AttributeMap(attrs - key)

  lazy val contentHash: Int =
    ContentHash.ofMap(attrs)(using summon[ContentHashable[String]], summon[ContentHashable[Attribute]])

object AttributeMap:
  val empty: AttributeMap = AttributeMap(Map.empty)

  def apply(attrs: Attribute*): AttributeMap =
    AttributeMap(attrs.map(a => a.key -> a).toMap)

  def fromMap(map: Map[String, Attribute]): AttributeMap =
    AttributeMap(map)

  given ContentHashable[AttributeMap] with
    def contentHash(a: AttributeMap): Int = a.contentHash
