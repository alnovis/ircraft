package io.alnovis.ircraft.java

import io.alnovis.ircraft.core.ir.Meta

import java.util.Optional

/**
  * Java-friendly wrapper over the Scala {@code Meta} opaque type.
  *
  * <p>{@code Meta} in ircraft core is an opaque type with extension methods -- a pattern
  * that is not accessible from Java. This class wraps it with regular instance methods,
  * providing a familiar key-value metadata store.</p>
  *
  * <p>{@code IrMeta} is immutable: every mutation method returns a new instance.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * import io.alnovis.ircraft.core.ir.Meta;
  *
  * Meta.Key<String> ORIGIN = Meta.Key.of("origin", String.class);
  *
  * IrMeta meta = IrMeta.empty()
  *     .set(ORIGIN, "proto")
  *     .set(Meta.Key.of("version", Integer.class), 3);
  *
  * Optional<String> origin = meta.get(ORIGIN);   // Optional["proto"]
  * boolean has = meta.contains(ORIGIN);           // true
  * IrMeta without = meta.remove(ORIGIN);          // version=3 only
  * }}}
  *
  * @see [[IrNode]]            for IR declarations that carry metadata
  * @see [[IrCompilationUnit]] for compilation units that carry metadata
  * @see [[IrModule]]          for modules that carry metadata
  */
final class IrMeta private (private val underlying: Meta) {

  /**
    * Returns {@code true} if this metadata contains no key-value pairs.
    *
    * @return {@code true} if empty, {@code false} otherwise
    */
  def isEmpty: Boolean = underlying.isEmpty

  /**
    * Returns {@code true} if this metadata contains a value for the given key.
    *
    * @param key the metadata key to check
    * @tparam A  the value type of the key
    * @return {@code true} if a value is associated with the key
    */
  def contains[A](key: Meta.Key[A]): Boolean = underlying.contains(key)

  /**
    * Retrieves the value associated with the given key, if present.
    *
    * @param key the metadata key to look up
    * @tparam A  the value type of the key
    * @return an {@code Optional} containing the value, or {@code Optional.empty()} if absent
    */
  def get[A](key: Meta.Key[A]): Optional[A] = {
    val opt: Option[A] = underlying.get(key)
    opt match {
      case Some(v) => Optional.of(v)
      case None    => Optional.empty()
    }
  }

  /**
    * Returns a new {@code IrMeta} with the given key-value pair added or replaced.
    *
    * <p>This method does not mutate the current instance.</p>
    *
    * @param key   the metadata key
    * @param value the value to associate
    * @tparam A    the value type of the key
    * @return a new {@code IrMeta} containing the updated mapping
    */
  def set[A](key: Meta.Key[A], value: A): IrMeta =
    new IrMeta(underlying.set(key, value))

  /**
    * Returns a new {@code IrMeta} with the given key removed.
    *
    * <p>If the key is not present, returns an equivalent copy.</p>
    *
    * @param key the metadata key to remove
    * @tparam A  the value type of the key
    * @return a new {@code IrMeta} without the specified key
    */
  def remove[A](key: Meta.Key[A]): IrMeta =
    new IrMeta(underlying.remove(key))

  /**
    * Returns the underlying Scala {@code Meta} value.
    *
    * <p>Use this when bridging back to Scala-based ircraft APIs.</p>
    *
    * @return the Scala {@code Meta} instance
    */
  def toScala: Meta = underlying

  override def toString: String = s"IrMeta(keys=${underlying.keys.map(_.name).mkString(", ")})"

  override def equals(obj: Any): Boolean = obj match {
    case other: IrMeta => underlying == other.underlying
    case _             => false
  }

  override def hashCode(): Int = underlying.hashCode()
}

/**
  * Factory methods for creating {@link IrMeta} instances.
  *
  * <h3>Usage from Java</h3>
  * {{{
  * import io.alnovis.ircraft.core.ir.Meta;
  *
  * // Empty metadata
  * IrMeta empty = IrMeta.empty();
  *
  * // Single key-value pair
  * IrMeta single = IrMeta.of(Meta.Key.of("source", String.class), "proto");
  *
  * // Two key-value pairs
  * IrMeta pair = IrMeta.of(
  *     Meta.Key.of("source", String.class), "proto",
  *     Meta.Key.of("version", Integer.class), 2
  * );
  * }}}
  *
  * @see [[IrMeta]]
  */
object IrMeta {

  /**
    * Returns an empty {@code IrMeta} with no key-value pairs.
    *
    * @return the singleton empty metadata instance
    */
  val empty: IrMeta = new IrMeta(Meta.empty)

  /**
    * Creates an {@code IrMeta} containing a single key-value pair.
    *
    * @param key   the metadata key
    * @param value the value to associate
    * @tparam A    the value type of the key
    * @return a new {@code IrMeta} with one entry
    */
  def of[A](key: Meta.Key[A], value: A): IrMeta =
    new IrMeta(Meta.empty.set(key, value))

  /**
    * Creates an {@code IrMeta} containing two key-value pairs.
    *
    * @param k1 the first metadata key
    * @param v1 the value for the first key
    * @param k2 the second metadata key
    * @param v2 the value for the second key
    * @tparam A the value type of the first key
    * @tparam B the value type of the second key
    * @return a new {@code IrMeta} with two entries
    */
  def of[A, B](k1: Meta.Key[A], v1: A, k2: Meta.Key[B], v2: B): IrMeta =
    new IrMeta(Meta.empty.set(k1, v1).set(k2, v2))

  /**
    * Wraps an existing Scala {@code Meta} value as an {@code IrMeta}.
    *
    * <p>This is the primary bridge from Scala-based ircraft APIs into the Java facade.</p>
    *
    * @param meta the Scala {@code Meta} to wrap
    * @return a new {@code IrMeta} wrapping the given value
    */
  def fromScala(meta: Meta): IrMeta = new IrMeta(meta)
}
