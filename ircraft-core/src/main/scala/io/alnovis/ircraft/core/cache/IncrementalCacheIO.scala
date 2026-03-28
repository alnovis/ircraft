package io.alnovis.ircraft.core.cache

import java.nio.file.{ Files, Path }
import java.nio.charset.StandardCharsets

object IncrementalCacheIO:

  /** Load cache from `cacheDir/ir-cache.json`. Returns None if file missing or corrupt. */
  def load(cacheDir: Path): Option[IncrementalCache] =
    val file = cacheDir.resolve(IncrementalCache.CacheFileName)
    if !Files.exists(file) then return None
    try
      val json = new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
      Some(JsonReader.parse(json))
    catch case _: Exception => None

  /** Save cache to `cacheDir/ir-cache.json`. Creates directories if needed. */
  def save(cacheDir: Path, cache: IncrementalCache): Unit =
    Files.createDirectories(cacheDir)
    val json = JsonWriter.write(cache)
    Files.write(cacheDir.resolve(IncrementalCache.CacheFileName), json.getBytes(StandardCharsets.UTF_8))

  // -- JSON Writer (no external deps) ----------------------------------------

  private[cache] object JsonWriter:

    def write(cache: IncrementalCache): String =
      val sb = new StringBuilder
      sb.append("{\n")
      sb.append(s"""  "version": ${str(cache.version)},\n""")
      sb.append("""  "entities": {""")
      writeEntities(sb, cache.entities)
      sb.append(""",""").append('\n')
      sb.append("""  "globalFiles": """)
      writeStringSet(sb, cache.globalFiles)
      sb.append('\n')
      sb.append("}")
      sb.toString

    private def writeEntities(sb: StringBuilder, entities: Map[String, CacheEntry]): Unit =
      if entities.isEmpty then
        sb.append("}")
        return
      sb.append('\n')
      val sorted = entities.toList.sortBy(_._1)
      for ((name, entry), idx) <- sorted.zipWithIndex do
        sb.append(s"    ${str(name)}: {")
        sb.append(s""" "contentHash": ${entry.contentHash}, "filePaths": """)
        writeStringSet(sb, entry.filePaths)
        sb.append(" }")
        if idx < sorted.size - 1 then sb.append(',')
        sb.append('\n')
      sb.append("  }")

    private def writeStringSet(sb: StringBuilder, set: Set[String]): Unit =
      val sorted = set.toList.sorted
      sb.append('[')
      for (s, idx) <- sorted.zipWithIndex do
        sb.append(str(s))
        if idx < sorted.size - 1 then sb.append(", ")
      sb.append(']')

    private def str(s: String): String =
      val sb = new StringBuilder("\"")
      for c <- s do
        c match
          case '"'  => sb.append("\\\"")
          case '\\' => sb.append("\\\\")
          case '\n' => sb.append("\\n")
          case '\t' => sb.append("\\t")
          case '\r' => sb.append("\\r")
          case _    => sb.append(c)
      sb.append('"')
      sb.toString

  // -- JSON Reader (minimal recursive-descent) -------------------------------

  private[cache] object JsonReader:

    def parse(json: String): IncrementalCache =
      val p = new Parser(json)
      p.parseCache()

    private class Parser(input: String):
      private var pos: Int = 0

      def parseCache(): IncrementalCache =
        expectChar('{')
        var version     = ""
        var entities    = Map.empty[String, CacheEntry]
        var globalFiles = Set.empty[String]

        while peek() != '}' do
          val key = readString()
          expectChar(':')
          key match
            case "version"     => version = readString()
            case "entities"    => entities = readEntities()
            case "globalFiles" => globalFiles = readStringArray().toSet
            case _             => skipValue()
          if peek() == ',' then advance()

        expectChar('}')
        IncrementalCache(version, entities, globalFiles)

      private def readEntities(): Map[String, CacheEntry] =
        expectChar('{')
        var map = Map.empty[String, CacheEntry]
        while peek() != '}' do
          val name = readString()
          expectChar(':')
          val entry = readCacheEntry()
          map = map.updated(name, entry)
          if peek() == ',' then advance()
        expectChar('}')
        map

      private def readCacheEntry(): CacheEntry =
        expectChar('{')
        var hash  = 0
        var files = Set.empty[String]
        while peek() != '}' do
          val key = readString()
          expectChar(':')
          key match
            case "contentHash" => hash = readInt()
            case "filePaths"   => files = readStringArray().toSet
            case _             => skipValue()
          if peek() == ',' then advance()
        expectChar('}')
        CacheEntry(hash, files)

      private def readString(): String =
        skipWhitespace()
        expectChar('"')
        val sb = new StringBuilder
        while input(pos) != '"' do
          if input(pos) == '\\' then
            pos += 1
            input(pos) match
              case '"'  => sb.append('"')
              case '\\' => sb.append('\\')
              case 'n'  => sb.append('\n')
              case 't'  => sb.append('\t')
              case 'r'  => sb.append('\r')
              case c    => sb.append(c)
          else sb.append(input(pos))
          pos += 1
        pos += 1 // closing "
        sb.toString

      private def readInt(): Int =
        skipWhitespace()
        val start = pos
        if input(pos) == '-' then pos += 1
        while pos < input.length && input(pos).isDigit do pos += 1
        input.substring(start, pos).toInt

      private def readStringArray(): List[String] =
        expectChar('[')
        var list = List.empty[String]
        while peek() != ']' do
          list = list :+ readString()
          if peek() == ',' then advance()
        expectChar(']')
        list

      private def skipValue(): Unit =
        skipWhitespace()
        input(pos) match
          case '"' => readString()
          case '{' =>
            advance(); var depth = 1
            while depth > 0 do
              if input(pos) == '{' then depth += 1
              else if input(pos) == '}' then depth -= 1
              if input(pos) == '"' then readString()
              else pos += 1
          case '[' =>
            advance(); var depth = 1
            while depth > 0 do
              if input(pos) == '[' then depth += 1
              else if input(pos) == ']' then depth -= 1
              if input(pos) == '"' then readString()
              else pos += 1
          case _ =>
            while pos < input.length && input(pos) != ',' && input(pos) != '}' && input(pos) != ']' do pos += 1

      private def peek(): Char =
        skipWhitespace()
        input(pos)

      private def advance(): Unit = pos += 1

      private def expectChar(c: Char): Unit =
        skipWhitespace()
        if input(pos) != c then
          throw new IllegalArgumentException(
            s"Expected '$c' at position $pos, got '${input(pos)}'"
          )
        pos += 1

      private def skipWhitespace(): Unit =
        while pos < input.length && input(pos).isWhitespace do pos += 1
