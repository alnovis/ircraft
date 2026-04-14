package io.alnovis.ircraft.core.ir

import cats.Monoid

/**
  * Type-safe, extensible metadata map.
  *
  * `Meta` is an opaque type backed by `Map[Meta.Key[?], Any]`. Values are stored
  * and retrieved using identity-based [[Meta.Key]] instances, which carry phantom
  * type information to ensure type-safe access at retrieval time.
  *
  * This design is inspired by the "vault" pattern: each [[Meta.Key]] allocation
  * is unique by reference identity, so two keys with the same name but created
  * separately are distinct. This prevents type erasure issues with generic type
  * parameters.
  *
  * {{{
  * val myKey = Meta.Key[String]("myKey")
  * val m     = Meta.empty.set(myKey, "hello")
  * val v     = m.get(myKey) // Some("hello")
  * }}}
  *
  * @see [[Meta.Key]] for typed metadata keys
  * @see [[Meta.Entry]] for constructing metadata entries before applying them
  */
opaque type Meta = Map[Meta.Key[?], Any]

/**
  * Companion object for [[Meta]] providing constructors, a `cats.Monoid` instance,
  * the [[Meta.Key]] class, and extension methods for type-safe access.
  */
object Meta:

  /** The empty metadata map containing no entries. */
  val empty: Meta = Map.empty

  /**
    * `cats.Monoid` instance for [[Meta]].
    *
    * Combining two `Meta` values merges their entries; keys from the right-hand
    * side take precedence over duplicate keys from the left-hand side.
    */
  given Monoid[Meta] with
    def empty: Meta                     = Meta.empty
    def combine(x: Meta, y: Meta): Meta = x ++ y

  /**
    * A deferred metadata entry that can be applied to a [[Meta]] map.
    *
    * Useful for building metadata in a builder-like fashion via [[Meta.of]].
    *
    * @param f the transformation function encapsulated by this entry
    */
  case class Entry(private val f: Meta => Meta):
    /**
      * Applies this entry's transformation to the given metadata map.
      *
      * @param m the metadata map to transform
      * @return the updated metadata map
      */
    def applyTo(m: Meta): Meta = f(m)

  /**
    * Creates a [[Meta.Entry]] that sets the given key-value pair.
    *
    * @tparam A the value type
    * @param key   the metadata key
    * @param value the metadata value
    * @return an [[Entry]] that, when applied, sets `key` to `value`
    */
  def entry[A](key: Key[A], value: A): Entry = Entry(_.set(key, value))

  /**
    * Constructs a [[Meta]] from zero or more [[Entry]] instances.
    *
    * {{{
    * val m = Meta.of(
    *   Meta.entry(keyA, "hello"),
    *   Meta.entry(keyB, 42)
    * )
    * }}}
    *
    * @param entries the entries to apply, in order
    * @return the resulting metadata map
    */
  def of(entries: Entry*): Meta = entries.foldLeft(empty)((m, e) => e.applyTo(m))

  /**
    * Typed metadata key with identity-based equality (vault-style).
    *
    * Each `Key` allocation is unique -- two keys with the same name but created
    * separately are distinct. This prevents type erasure issues with generic
    * type parameters.
    *
    * {{{
    * val k1 = Meta.Key[String]("foo")
    * val k2 = Meta.Key[String]("foo")
    * k1 == k2 // false -- different allocations
    * }}}
    *
    * @tparam A the type of values stored under this key
    * @param name a human-readable name for debugging purposes
    */
  final class Key[A] private[Meta] (val name: String):
    override def toString: String = s"Meta.Key($name)"

  /** Factory for [[Key]] instances. */
  object Key:
    /**
      * Creates a new unique metadata key with the given name.
      *
      * @tparam A the type of values stored under this key
      * @param name a human-readable name for debugging purposes
      * @return a new unique [[Key]] instance
      */
    def apply[A](name: String): Key[A] = new Key[A](name)

  /** Extension methods for type-safe access to [[Meta]] entries. */
  extension (m: Meta)

    /**
      * Retrieves the value associated with the given key, if present.
      *
      * @tparam A the expected value type
      * @param key the metadata key to look up
      * @return `Some(value)` if the key is present, `None` otherwise
      */
    def get[A](key: Key[A]): Option[A] =
      m.get(key).map(_.asInstanceOf[A])

    /**
      * Returns a new [[Meta]] with the given key-value pair set (or replaced).
      *
      * @tparam A the value type
      * @param key   the metadata key
      * @param value the value to associate with the key
      * @return an updated metadata map
      */
    def set[A](key: Key[A], value: A): Meta =
      m.updated(key, value)

    /**
      * Returns a new [[Meta]] with the given key removed.
      *
      * @tparam A the value type of the key
      * @param key the metadata key to remove
      * @return an updated metadata map without the specified key
      */
    def remove[A](key: Key[A]): Meta =
      m.removed(key)

    /**
      * Tests whether the given key is present in this metadata map.
      *
      * @tparam A the value type of the key
      * @param key the metadata key to check
      * @return `true` if the key is present, `false` otherwise
      */
    def contains[A](key: Key[A]): Boolean =
      m.contains(key)

    /**
      * Tests whether this metadata map contains no entries.
      *
      * @return `true` if the map is empty, `false` otherwise
      */
    def isEmpty: Boolean = m.isEmpty

    /**
      * Returns the set of all keys present in this metadata map.
      *
      * @return the set of [[Key]] instances
      */
    def keys: Set[Key[?]] = m.keySet
