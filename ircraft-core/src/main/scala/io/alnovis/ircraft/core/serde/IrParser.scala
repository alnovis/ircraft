package io.alnovis.ircraft.core.serde

import io.alnovis.ircraft.core.*

/**
  * Recursive descent parser for the textual IR format produced by IrPrinter.
  *
  * Produces GenericOp instances (dialect-agnostic). The consumer uses `kind` pattern matching to reconstruct typed
  * operations if needed.
  */
object IrParser:

  /** Parse a complete module from textual IR. */
  def parse(text: String): Either[ParseError, IrModule] =
    try Right(new Parser(text).parseModule())
    catch case e: ParserException => Left(e.toParseError)

  /** Parse a single operation from textual IR. */
  def parseOp(text: String): Either[ParseError, GenericOp] =
    try Right(new Parser(text).parseOperation())
    catch case e: ParserException => Left(e.toParseError)

  private class ParserException(msg: String, val line: Int, val column: Int)
      extends RuntimeException(s"$line:$column: $msg"):
    def toParseError: ParseError = ParseError(msg, line, column)

  private class Parser(input: String):
    private var pos: Int = 0

    // -- Line/column tracking -----------------------------------------------

    private def lineAndColumn: (Int, Int) =
      var line = 1
      var col  = 1
      var i    = 0
      while i < pos && i < input.length do
        if input(i) == '\n' then
          line += 1
          col = 1
        else col += 1
        i += 1
      (line, col)

    private def error(msg: String): ParserException =
      val (line, col) = lineAndColumn
      new ParserException(msg, line, col)

    // -- IrModule -------------------------------------------------------------

    def parseModule(): IrModule =
      skipWs()
      expectKeyword("module")
      val name  = readQuotedString()
      val attrs = if peekChar() == '[' then parseAttrs() else AttributeMap.empty
      expectChar('{')
      val ops = parseOperations()
      expectChar('}')
      IrModule(name, ops.toVector, attrs)

    // -- Operations ---------------------------------------------------------

    def parseOperation(): GenericOp =
      skipWs()
      val qualName = readQualifiedName()
      val dot      = qualName.indexOf('.')
      val kind =
        if dot > 0 then NodeKind(qualName.substring(0, dot), qualName.substring(dot + 1))
        else NodeKind("", qualName)
      val attrs = if peekChar() == '[' then parseAttrs() else AttributeMap.empty
      val regions =
        if peekChar() == '{' then
          expectChar('{')
          val regs = parseRegions()
          expectChar('}')
          regs
        else Vector.empty
      GenericOp(kind, attrs, regions)

    private def parseOperations(): List[GenericOp] =
      val ops = List.newBuilder[GenericOp]
      while
        skipWs()
        pos < input.length && peekChar() != '}'
      do ops += parseOperation()
      ops.result()

    // -- Regions ------------------------------------------------------------

    private def parseRegions(): Vector[Region] =
      val regions = Vector.newBuilder[Region]
      while
        skipWs()
        pos < input.length && peekChar() != '}'
      do
        // Could be a region name or an operation. Region names are simple idents
        // followed by '{'. Operations are qualified names (with dot).
        val saved = pos
        val ident = readIdent()
        skipWs()
        if peekChar() == '{' then
          // It's a region
          expectChar('{')
          val ops = parseOperations()
          expectChar('}')
          regions += Region(ident, ops.toVector)
        else
          // It's actually an operation starting with what looked like an ident.
          // Backtrack and parse as operation.
          pos = saved
          // Single unnamed region containing remaining operations
          val ops = parseOperations()
          if ops.nonEmpty then regions += Region("body", ops.toVector)
      regions.result()

    // -- Attributes ---------------------------------------------------------

    private def parseAttrs(): AttributeMap =
      expectChar('[')
      var attrs = AttributeMap.empty
      while
        skipWs()
        peekChar() != ']'
      do
        val key = readIdent()
        expectChar('=')
        val attr = parseAttrValue(key)
        attrs = attrs + attr
        skipWs()
        if peekChar() == ',' then advance()
      expectChar(']')
      attrs

    private def parseAttrValue(key: String): Attribute =
      skipWs()
      peekChar() match
        case '"' =>
          // String
          Attribute.StringAttr(key, readQuotedString())
        case '@' =>
          // Ref
          advance() // skip @
          Attribute.RefAttr(key, NodeId(readInteger()))
        case '[' =>
          // List -- disambiguate by content
          parseListAttr(key)
        case '{' =>
          // AttrMap
          parseAttrMap(key)
        case c if c == 't' || c == 'f' =>
          // Boolean
          Attribute.BoolAttr(key, readBool())
        case c if c == '-' || c.isDigit =>
          // Int or Long (Long has 'L' suffix)
          val num = readLongOrInt()
          num match
            case Left(i)  => Attribute.IntAttr(key, i)
            case Right(l) => Attribute.LongAttr(key, l)
        case c =>
          throw error(s"Unexpected character '$c' in attribute value")

    private def parseListAttr(key: String): Attribute =
      expectChar('[')
      skipWs()
      if peekChar() == ']' then
        // Empty list -- default to StringListAttr
        expectChar(']')
        return Attribute.StringListAttr(key, Nil)

      // Peek to determine list type
      peekChar() match
        case '"' =>
          // Could be StringListAttr (bare strings) or AttrListAttr with StringAttr entries.
          // StringListAttr: ["a", "b"]. AttrListAttr: [key="val", ...].
          // Since IrPrinter outputs StringListAttr as ["a","b"] (bare strings, no key=),
          // bare string = StringListAttr.
          val items = List.newBuilder[String]
          while
            skipWs()
            peekChar() != ']'
          do
            items += readQuotedString()
            skipWs()
            if peekChar() == ',' then advance()
          expectChar(']')
          Attribute.StringListAttr(key, items.result())
        case c if c == '-' || c.isDigit =>
          // IntListAttr (bare integers)
          val items = List.newBuilder[Int]
          while
            skipWs()
            peekChar() != ']'
          do
            items += readInteger()
            skipWs()
            if peekChar() == ',' then advance()
          expectChar(']')
          Attribute.IntListAttr(key, items.result())
        case _ =>
          // AttrListAttr (key=value entries)
          val items = List.newBuilder[Attribute]
          while
            skipWs()
            peekChar() != ']'
          do
            val attrKey = readIdent()
            expectChar('=')
            items += parseAttrValue(attrKey)
            skipWs()
            if peekChar() == ',' then advance()
          expectChar(']')
          Attribute.AttrListAttr(key, items.result())

    private def parseAttrMap(key: String): Attribute =
      expectChar('{')
      val entries = Map.newBuilder[String, Attribute]
      while
        skipWs()
        peekChar() != '}'
      do
        val mk = readIdent()
        expectChar('=')
        val mv = parseAttrValue(mk)
        entries += (mk -> mv)
        skipWs()
        if peekChar() == ',' then advance()
      expectChar('}')
      Attribute.AttrMapAttr(key, entries.result())

    // -- Lexer primitives ---------------------------------------------------

    private def readQuotedString(): String =
      skipWs()
      if peekChar() != '"' then throw error(s"Expected '\"', got '${peekChar()}'")
      advance()
      val sb = new StringBuilder
      while pos < input.length && input(pos) != '"' do
        if input(pos) == '\\' then
          pos += 1
          if pos >= input.length then throw error("Unexpected end of input in string escape")
          input(pos) match
            case '"'  => sb.append('"')
            case '\\' => sb.append('\\')
            case 'n'  => sb.append('\n')
            case 't'  => sb.append('\t')
            case 'r'  => sb.append('\r')
            case c    => sb.append(c)
        else sb.append(input(pos))
        pos += 1
      if pos >= input.length then throw error("Unterminated string")
      advance() // closing "
      sb.toString

    private def readIdent(): String =
      skipWs()
      val start = pos
      while pos < input.length && isIdentChar(input(pos)) do pos += 1
      if pos == start then
        throw error(s"Expected identifier, got '${if pos < input.length then input(pos) else "EOF"}'")
      input.substring(start, pos)

    private def readQualifiedName(): String =
      skipWs()
      val start = pos
      while pos < input.length && (isIdentChar(input(pos)) || input(pos) == '.') do pos += 1
      if pos == start then throw error("Expected qualified name")
      input.substring(start, pos)

    private def readInteger(): Int =
      skipWs()
      val start = pos
      if pos < input.length && input(pos) == '-' then pos += 1
      while pos < input.length && input(pos).isDigit do pos += 1
      if pos == start then throw error("Expected integer")
      input.substring(start, pos).toInt

    /** Read a number, returning Left(Int) or Right(Long) based on 'L' suffix. */
    private def readLongOrInt(): Either[Int, Long] =
      skipWs()
      val start = pos
      if pos < input.length && input(pos) == '-' then pos += 1
      while pos < input.length && input(pos).isDigit do pos += 1
      val numStr = input.substring(start, pos)
      if pos < input.length && (input(pos) == 'L' || input(pos) == 'l') then
        advance() // consume 'L'
        Right(numStr.toLong)
      else Left(numStr.toInt)

    private def readBool(): Boolean =
      skipWs()
      if input.startsWith("true", pos) then
        pos += 4; true
      else if input.startsWith("false", pos) then
        pos += 5; false
      else throw error("Expected 'true' or 'false'")

    private def isIdentChar(c: Char): Boolean =
      c.isLetterOrDigit || c == '_' || c == '-'

    private def peekChar(): Char =
      skipWs()
      if pos >= input.length then throw error("Unexpected end of input")
      input(pos)

    private def advance(): Unit = pos += 1

    private def expectChar(c: Char): Unit =
      skipWs()
      if pos >= input.length then throw error(s"Expected '$c', got end of input")
      if input(pos) != c then throw error(s"Expected '$c', got '${input(pos)}'")
      pos += 1

    private def expectKeyword(kw: String): Unit =
      skipWs()
      if !input.startsWith(kw, pos) then throw error(s"Expected '$kw'")
      pos += kw.length
      // Must be followed by non-ident char
      if pos < input.length && isIdentChar(input(pos)) then throw error(s"Expected '$kw'")

    private def skipWs(): Unit =
      while pos < input.length do
        if input(pos).isWhitespace then pos += 1
        else if input.startsWith("//", pos) then
          // Skip line comment
          while pos < input.length && input(pos) != '\n' do pos += 1
        else return
