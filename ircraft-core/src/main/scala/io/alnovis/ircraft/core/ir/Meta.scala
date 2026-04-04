package io.alnovis.ircraft.core.ir

import cats.Monoid

opaque type Meta = Map[Meta.Key[?], Any]

object Meta:
  val empty: Meta = Map.empty

  given Monoid[Meta] with
    def empty: Meta = Meta.empty
    def combine(x: Meta, y: Meta): Meta = x ++ y

  /** Type-safe entry for constructing Meta. */
  case class Entry(private val f: Meta => Meta):
    def applyTo(m: Meta): Meta = f(m)

  def entry[A](key: Key[A], value: A): Entry = Entry(_.set(key, value))

  def of(entries: Entry*): Meta = entries.foldLeft(empty)((m, e) => e.applyTo(m))

  /** Typed metadata key. Identity-based equality (vault-style).
    * Each `Key` allocation is unique — two keys with the same name
    * but created separately are distinct. This prevents type erasure
    * issues with generic type parameters.
    */
  final class Key[A] private[Meta] (val name: String):
    override def toString: String = s"Meta.Key($name)"

  object Key:
    def apply[A](name: String): Key[A] = new Key[A](name)

  extension (m: Meta)
    def get[A](key: Key[A]): Option[A] =
      m.get(key).map(_.asInstanceOf[A])

    def set[A](key: Key[A], value: A): Meta =
      m.updated(key, value)

    def remove[A](key: Key[A]): Meta =
      m.removed(key)

    def contains[A](key: Key[A]): Boolean =
      m.contains(key)

    def isEmpty: Boolean = m.isEmpty

    def keys: Set[Key[?]] = m.keySet
