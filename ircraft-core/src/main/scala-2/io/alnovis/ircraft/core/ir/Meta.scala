package io.alnovis.ircraft.core.ir

import cats.Monoid

final class Meta private (private val underlying: Map[Meta.Key[_], Any]) {
  def get[A](key: Meta.Key[A]): Option[A] =
    underlying.get(key).map(_.asInstanceOf[A])

  def set[A](key: Meta.Key[A], value: A): Meta =
    new Meta(underlying.updated(key, value))

  def remove[A](key: Meta.Key[A]): Meta =
    new Meta(underlying - key)

  def contains[A](key: Meta.Key[A]): Boolean =
    underlying.contains(key)

  def isEmpty: Boolean = underlying.isEmpty

  def keys: Set[Meta.Key[_]] = underlying.keySet

  override def equals(obj: Any): Boolean = obj match {
    case other: Meta => underlying == other.underlying
    case _ => false
  }

  override def hashCode(): Int = underlying.hashCode()

  override def toString: String = s"Meta(${underlying.toString})"
}

object Meta {
  val empty: Meta = new Meta(Map.empty)

  implicit val monoid: Monoid[Meta] = new Monoid[Meta] {
    def empty: Meta = Meta.empty
    def combine(x: Meta, y: Meta): Meta =
      new Meta(x.underlying ++ y.underlying)
  }

  final class Key[A] private[Meta] (val name: String) {
    override def toString: String = s"Meta.Key($name)"
  }

  object Key {
    def apply[A](name: String): Key[A] = new Key[A](name)
  }
}
