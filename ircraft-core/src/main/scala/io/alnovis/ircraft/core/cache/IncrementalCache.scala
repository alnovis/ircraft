package io.alnovis.ircraft.core.cache

case class CacheEntry(contentHash: Int, filePaths: Set[String])

case class IncrementalCache(
  version: String,
  entities: Map[String, CacheEntry],
  globalFiles: Set[String]
):

  /** Entity names whose hash matches the current hash. */
  def unchangedEntities(currentHashes: Map[String, Int]): Set[String] =
    entities.collect {
      case (name, entry) if currentHashes.get(name).contains(entry.contentHash) => name
    }.toSet

  /** Entity names whose hash differs or that are new. */
  def changedEntities(currentHashes: Map[String, Int]): Set[String] =
    val allNames = entities.keySet ++ currentHashes.keySet
    allNames -- unchangedEntities(currentHashes)

  /** Update a single entity entry. */
  def update(name: String, hash: Int, files: Set[String]): IncrementalCache =
    copy(entities = entities.updated(name, CacheEntry(hash, files)))

  /** Update the global files set. */
  def withGlobalFiles(files: Set[String]): IncrementalCache =
    copy(globalFiles = files)

object IncrementalCache:
  val CurrentVersion: String = "1"
  val CacheFileName: String  = "ir-cache.json"

  def empty: IncrementalCache = IncrementalCache(CurrentVersion, Map.empty, Set.empty)
