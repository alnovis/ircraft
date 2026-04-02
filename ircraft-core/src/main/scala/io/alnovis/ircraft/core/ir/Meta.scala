package io.alnovis.ircraft.core.ir

import scala.reflect.ClassTag

opaque type Meta = Map[Meta.Key[?], Any]

object Meta:
  val empty: Meta = Map.empty

  /** Type-safe entry for constructing Meta. */
  case class Entry(private val f: Meta => Meta):
    def applyTo(m: Meta): Meta = f(m)

  def entry[A](key: Key[A], value: A): Entry = Entry(_.set(key, value))

  def of(entries: Entry*): Meta = entries.foldLeft(empty)((m, e) => e.applyTo(m))

  /** Typed metadata key. ClassTag ensures Key[Int]("x") != Key[String]("x"). */
  case class Key[A](name: String)(using val ct: ClassTag[A]):
    override def equals(that: Any): Boolean = that match
      case k: Key[?] => name == k.name && ct == k.ct
      case _         => false
    override def hashCode: Int = (name, ct).hashCode

  extension (m: Meta)
    def get[A](key: Key[A]): Option[A] =
      m.get(key).collect { case v if key.ct.runtimeClass.isInstance(v) => v.asInstanceOf[A] }

    def set[A](key: Key[A], value: A): Meta =
      m.updated(key, value)

    def remove[A](key: Key[A]): Meta =
      m.removed(key)

    def contains[A](key: Key[A]): Boolean =
      m.contains(key)

    def isEmpty: Boolean = m.isEmpty

    def keys: Set[Key[?]] = m.keySet
