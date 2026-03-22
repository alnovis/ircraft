package io.alnovis.ircraft.core

/**
  * Utilities for computing content-based hashes of IR nodes.
  *
  * Uses a fast, non-cryptographic mixing strategy for combining field hashes. SHA-256 is available for when
  * content-addressable identity across processes is needed.
  */
object ContentHash:

  /** Combine multiple hash codes into one using MurmurHash3-like mixing. */
  def combine(hashes: Int*): Int =
    var h = 0xCAFEBABE
    for hash <- hashes do h = mix(h, hash)
    finalizeHash(h, hashes.length)

  /** Mix a single value into an accumulator. */
  def mix(acc: Int, value: Int): Int =
    var k = value
    k *= 0xCC9E2D51
    k = Integer.rotateLeft(k, 15)
    k *= 0x1B873593
    var h = acc ^ k
    h = Integer.rotateLeft(h, 13)
    h = h * 5 + 0xE6546B64
    h

  /** Finalize hash with avalanche. */
  def finalizeHash(h: Int, length: Int): Int =
    var hash = h ^ length
    hash ^= hash >>> 16
    hash *= 0x85EBCA6B
    hash ^= hash >>> 13
    hash *= 0xC2B2AE35
    hash ^= hash >>> 16
    hash

  /** Hash a string. */
  def ofString(s: String): Int = if s == null then 0 else s.hashCode

  /** Hash an int. */
  def ofInt(i: Int): Int = i

  /** Hash a long. */
  def ofLong(l: Long): Int = (l ^ (l >>> 32)).toInt

  /** Hash a boolean. */
  def ofBoolean(b: Boolean): Int = if b then 1231 else 1237

  /** Hash an optional value. */
  def ofOption[A](opt: Option[A])(using h: ContentHashable[A]): Int =
    opt match
      case Some(a) => mix(1, h.contentHash(a))
      case None    => 0

  /** Hash a list of hashable values. */
  def ofList[A](list: List[A])(using h: ContentHashable[A]): Int =
    var acc = 0xCAFEBABE
    for a <- list do acc = mix(acc, h.contentHash(a))
    finalizeHash(acc, list.size)

  /** Hash a set of hashable values (order-independent). */
  def ofSet[A](set: Set[A])(using h: ContentHashable[A]): Int =
    var acc = 0
    for a <- set do acc ^= h.contentHash(a)
    finalizeHash(acc, set.size)

  /** Hash a map (order-independent). */
  def ofMap[K, V](map: Map[K, V])(using hk: ContentHashable[K], hv: ContentHashable[V]): Int =
    var acc = 0
    for (k, v) <- map do acc ^= mix(hk.contentHash(k), hv.contentHash(v))
    finalizeHash(acc, map.size)

/** Type class for content-addressable hashing. */
trait ContentHashable[A]:
  def contentHash(a: A): Int

object ContentHashable:
  def apply[A](using h: ContentHashable[A]): ContentHashable[A] = h

  given ContentHashable[Int] with
    def contentHash(a: Int): Int = a

  given ContentHashable[Long] with
    def contentHash(a: Long): Int = ContentHash.ofLong(a)

  given ContentHashable[String] with
    def contentHash(a: String): Int = ContentHash.ofString(a)

  given ContentHashable[Boolean] with
    def contentHash(a: Boolean): Int = ContentHash.ofBoolean(a)

  given [A](using h: ContentHashable[A]): ContentHashable[List[A]] with
    def contentHash(a: List[A]): Int = ContentHash.ofList(a)

  given [A](using h: ContentHashable[A]): ContentHashable[Set[A]] with
    def contentHash(a: Set[A]): Int = ContentHash.ofSet(a)

  given [A](using h: ContentHashable[A]): ContentHashable[Option[A]] with
    def contentHash(a: Option[A]): Int = ContentHash.ofOption(a)
