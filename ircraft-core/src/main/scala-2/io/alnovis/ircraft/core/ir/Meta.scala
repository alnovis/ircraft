package io.alnovis.ircraft.core.ir

import cats.Monoid

/**
  * A type-safe, heterogeneous metadata map for attaching arbitrary
  * key-value pairs to IR nodes.
  *
  * Unlike Scala 3's opaque type variant, this Scala 2 version is implemented
  * as a final class wrapping a `Map[Meta.Key[_], Any]`. Type safety is
  * enforced through the phantom type parameter on [[Meta.Key]].
  *
  * Typical usage:
  * {{{
  * val myKey = Meta.Key[String]("my.key")
  * val meta = Meta.empty.set(myKey, "hello")
  * val value: Option[String] = meta.get(myKey)
  * }}}
  *
  * @see [[Meta.Key]] for creating typed metadata keys
  * @see [[SemanticF]] for nodes that carry [[Meta]]
  */
final class Meta private (private val underlying: Map[Meta.Key[_], Any]) {

  /**
    * Retrieves the value associated with the given key, if present.
    *
    * @param key the typed metadata key
    * @tparam A the value type
    * @return `Some(value)` if the key is present, `None` otherwise
    */
  def get[A](key: Meta.Key[A]): Option[A] =
    underlying.get(key).map(_.asInstanceOf[A])

  /**
    * Returns a new [[Meta]] with the given key-value pair added or updated.
    *
    * @param key   the typed metadata key
    * @param value the value to associate with the key
    * @tparam A the value type
    * @return a new [[Meta]] containing the updated mapping
    */
  def set[A](key: Meta.Key[A], value: A): Meta =
    new Meta(underlying.updated(key, value))

  /**
    * Returns a new [[Meta]] with the given key removed.
    *
    * @param key the typed metadata key to remove
    * @tparam A the value type
    * @return a new [[Meta]] without the specified key
    */
  def remove[A](key: Meta.Key[A]): Meta =
    new Meta(underlying - key)

  /**
    * Tests whether the given key is present in this metadata map.
    *
    * @param key the typed metadata key to check
    * @tparam A the value type
    * @return `true` if the key exists, `false` otherwise
    */
  def contains[A](key: Meta.Key[A]): Boolean =
    underlying.contains(key)

  /**
    * Tests whether this metadata map contains no entries.
    *
    * @return `true` if the map is empty, `false` otherwise
    */
  def isEmpty: Boolean = underlying.isEmpty

  /**
    * Returns the set of all keys in this metadata map.
    *
    * @return the keys as a `Set[Meta.Key[_]]`
    */
  def keys: Set[Meta.Key[_]] = underlying.keySet

  override def equals(obj: Any): Boolean = obj match {
    case other: Meta => underlying == other.underlying
    case _ => false
  }

  override def hashCode(): Int = underlying.hashCode()

  override def toString: String = s"Meta(${underlying.toString})"
}

/**
  * Companion object for [[Meta]] providing the empty instance,
  * the [[cats.Monoid]] instance, and the [[Meta.Key]] factory.
  */
object Meta {

  /** The empty metadata map containing no entries. */
  val empty: Meta = new Meta(Map.empty)

  /**
    * Implicit [[cats.Monoid]] instance for [[Meta]].
    *
    * Combining two [[Meta]] instances merges their underlying maps,
    * with the right-hand side taking precedence on key conflicts.
    */
  implicit val monoid: Monoid[Meta] = new Monoid[Meta] {
    def empty: Meta = Meta.empty
    def combine(x: Meta, y: Meta): Meta =
      new Meta(x.underlying ++ y.underlying)
  }

  /**
    * A typed key for use with [[Meta]].
    *
    * The phantom type parameter `A` ensures type-safe retrieval:
    * a key created as `Key[String]("x")` can only be used to store
    * and retrieve `String` values.
    *
    * @param name the human-readable key name, used for debugging and display
    * @tparam A the type of value this key maps to
    */
  final class Key[A] private[Meta] (val name: String) {
    override def toString: String = s"Meta.Key($name)"
  }

  /**
    * Factory for creating [[Meta.Key]] instances.
    */
  object Key {

    /**
      * Creates a new typed metadata key with the given name.
      *
      * @param name the human-readable key name
      * @tparam A the type of value this key will map to
      * @return a new [[Meta.Key]] instance
      */
    def apply[A](name: String): Key[A] = new Key[A](name)
  }
}
