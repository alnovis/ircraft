package io.alnovis.ircraft.core.ir

opaque type Meta = Map[Meta.Key[?], Any]

object Meta:
  val empty: Meta = Map.empty

  def apply(entries: (Key[?], Any)*): Meta =
    entries.toMap

  case class Key[A](name: String)

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
