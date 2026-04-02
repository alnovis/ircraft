package io.alnovis.ircraft.core.ir

opaque type Meta = Map[Meta.Key[?], Any]

object Meta:
  val empty: Meta = Map.empty

  def apply(entries: (Key[?], Any)*): Meta =
    entries.toMap

  /** Typed metadata key. Same name + different type = different key (phantom A in equals). */
  case class Key[A](name: String)

  extension (m: Meta)
    /** Get a value by typed key. Returns None if key absent. Throws ClassCastException on type mismatch. */
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

    /** Safe get with type check -- returns None on type mismatch instead of throwing. */
    def getSafe[A](key: Key[A])(using ct: scala.reflect.ClassTag[A]): Option[A] =
      m.get(key).collect { case v if ct.runtimeClass.isInstance(v) => v.asInstanceOf[A] }
