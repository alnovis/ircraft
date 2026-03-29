package io.alnovis.ircraft.core.serde

import io.alnovis.ircraft.core.*

object IrJsonCodec:

  def toJson(module: Module): String =
    val sb = new StringBuilder
    JsonWriter.writeModule(sb, module)
    sb.toString

  def fromJson(json: String): Module =
    JsonReader.parseModule(json)

  def opToJson(op: Operation): String =
    val sb = new StringBuilder
    JsonWriter.writeOp(sb, op, 0)
    sb.toString

  // -- Writer ----------------------------------------------------------------

  private[serde] object JsonWriter:

    def writeModule(sb: StringBuilder, module: Module): Unit =
      sb.append("{\n")
      sb.append(s"""  "name": ${str(module.name)},\n""")
      sb.append("""  "attributes": """)
      writeAttrs(sb, module.attributes, 1)
      sb.append(",\n")
      sb.append("""  "operations": [""")
      writeOps(sb, module.topLevel, 1)
      sb.append("]\n")
      sb.append("}")

    def writeOp(sb: StringBuilder, op: Operation, indent: Int): Unit =
      val pad   = "  " * indent
      val inner = "  " * (indent + 1)
      sb.append(s"$pad{\n")
      sb.append(s"""$inner"kind": ${str(op.kind.qualifiedName)},\n""")
      sb.append(s"""$inner"attributes": """)
      writeAttrs(sb, op.attributes, indent + 1)
      sb.append(",\n")
      sb.append(s"""$inner"regions": [""")
      if op.regions.nonEmpty then
        sb.append('\n')
        for (r, idx) <- op.regions.zipWithIndex do
          writeRegion(sb, r, indent + 2)
          if idx < op.regions.size - 1 then sb.append(',')
          sb.append('\n')
        sb.append(s"$inner")
      sb.append("]\n")
      sb.append(s"$pad}")

    private def writeRegion(sb: StringBuilder, region: Region, indent: Int): Unit =
      val pad   = "  " * indent
      val inner = "  " * (indent + 1)
      sb.append(s"$pad{\n")
      sb.append(s"""$inner"name": ${str(region.name)},\n""")
      sb.append(s"""$inner"operations": [""")
      writeOps(sb, region.operations, indent + 1)
      sb.append(s"]\n")
      sb.append(s"$pad}")

    private def writeOps(sb: StringBuilder, ops: Vector[Operation], indent: Int): Unit =
      if ops.nonEmpty then
        sb.append('\n')
        for (op, idx) <- ops.zipWithIndex do
          writeOp(sb, op, indent + 1)
          if idx < ops.size - 1 then sb.append(',')
          sb.append('\n')
        sb.append("  " * indent)

    private def writeAttrs(sb: StringBuilder, attrs: AttributeMap, indent: Int): Unit =
      val pad    = "  " * (indent + 1)
      val sorted = attrs.values.toList.sortBy(_.key)
      sb.append('[')
      if sorted.nonEmpty then
        sb.append('\n')
        for (a, idx) <- sorted.zipWithIndex do
          sb.append(pad)
          writeAttr(sb, a)
          if idx < sorted.size - 1 then sb.append(',')
          sb.append('\n')
        sb.append("  " * indent)
      sb.append(']')

    private def writeAttr(sb: StringBuilder, attr: Attribute): Unit =
      attr match
        case Attribute.StringAttr(k, v) =>
          sb.append(s"""{"type": "string", "key": ${str(k)}, "value": ${str(v)}}""")
        case Attribute.IntAttr(k, v) =>
          sb.append(s"""{"type": "int", "key": ${str(k)}, "value": $v}""")
        case Attribute.LongAttr(k, v) =>
          sb.append(s"""{"type": "long", "key": ${str(k)}, "value": $v}""")
        case Attribute.BoolAttr(k, v) =>
          sb.append(s"""{"type": "bool", "key": ${str(k)}, "value": $v}""")
        case Attribute.StringListAttr(k, vs) =>
          sb.append(s"""{"type": "stringList", "key": ${str(k)}, "values": [${vs.map(str).mkString(", ")}]}""")
        case Attribute.IntListAttr(k, vs) =>
          sb.append(s"""{"type": "intList", "key": ${str(k)}, "values": [${vs.mkString(", ")}]}""")
        case Attribute.AttrListAttr(k, vs) =>
          sb.append(s"""{"type": "attrList", "key": ${str(k)}, "values": [""")
          for (a, idx) <- vs.zipWithIndex do
            writeAttr(sb, a)
            if idx < vs.size - 1 then sb.append(", ")
          sb.append("]}")
        case Attribute.AttrMapAttr(k, vs) =>
          sb.append(s"""{"type": "attrMap", "key": ${str(k)}, "values": [""")
          val entries = vs.toList.sortBy(_._1)
          for ((mk, mv), idx) <- entries.zipWithIndex do
            sb.append(s"""{"mapKey": ${str(mk)}, "attr": """)
            writeAttr(sb, mv)
            sb.append("}")
            if idx < entries.size - 1 then sb.append(", ")
          sb.append("]}")
        case Attribute.RefAttr(k, target) =>
          sb.append(s"""{"type": "ref", "key": ${str(k)}, "value": ${target.value}}""")

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

  // -- Reader ----------------------------------------------------------------

  private[serde] object JsonReader:

    def parseModule(json: String): Module =
      val p = new Parser(json)
      p.parseModule()

    private class Parser(input: String):
      private var pos: Int = 0

      def parseModule(): Module =
        expectChar('{')
        var name  = ""
        var attrs = AttributeMap.empty
        var ops   = Vector.empty[Operation]

        while peek() != '}' do
          val key = readString()
          expectChar(':')
          key match
            case "name"       => name = readString()
            case "attributes" => attrs = readAttrs()
            case "operations" => ops = readOps()
            case _            => skipValue()
          if peek() == ',' then advance()

        expectChar('}')
        Module(name, ops, attrs)

      private def readOps(): Vector[Operation] =
        expectChar('[')
        var ops = Vector.empty[Operation]
        while peek() != ']' do
          ops = ops :+ readOp()
          if peek() == ',' then advance()
        expectChar(']')
        ops

      private def readOp(): GenericOp =
        expectChar('{')
        var kind    = NodeKind("", "")
        var attrs   = AttributeMap.empty
        var regions = Vector.empty[Region]

        while peek() != '}' do
          val key = readString()
          expectChar(':')
          key match
            case "kind" =>
              val qn  = readString()
              val dot = qn.indexOf('.')
              kind = if dot > 0 then NodeKind(qn.substring(0, dot), qn.substring(dot + 1)) else NodeKind("", qn)
            case "attributes" => attrs = readAttrs()
            case "regions"    => regions = readRegions()
            case _            => skipValue()
          if peek() == ',' then advance()

        expectChar('}')
        GenericOp(kind, attrs, regions)

      private def readRegions(): Vector[Region] =
        expectChar('[')
        var regions = Vector.empty[Region]
        while peek() != ']' do
          regions = regions :+ readRegion()
          if peek() == ',' then advance()
        expectChar(']')
        regions

      private def readRegion(): Region =
        expectChar('{')
        var name = ""
        var ops  = Vector.empty[Operation]

        while peek() != '}' do
          val key = readString()
          expectChar(':')
          key match
            case "name"       => name = readString()
            case "operations" => ops = readOps()
            case _            => skipValue()
          if peek() == ',' then advance()

        expectChar('}')
        Region(name, ops)

      private def readAttrs(): AttributeMap =
        expectChar('[')
        var attrs = AttributeMap.empty
        while peek() != ']' do
          attrs = attrs + readAttr()
          if peek() == ',' then advance()
        expectChar(']')
        attrs

      private def readAttr(): Attribute =
        expectChar('{')
        var attrType      = ""
        var key           = ""
        var stringVal     = ""
        var intVal        = 0
        var longVal       = 0L
        var boolVal       = false
        var stringListVal = List.empty[String]
        var intListVal    = List.empty[Int]
        var attrListVal   = List.empty[Attribute]
        var attrMapVal    = List.empty[(String, Attribute)]
        var refVal        = 0

        while peek() != '}' do
          val k = readString()
          expectChar(':')
          k match
            case "type" => attrType = readString()
            case "key"  => key = readString()
            case "value" =>
              attrType match
                case "string" => stringVal = readString()
                case "int"    => intVal = readInt()
                case "long"   => longVal = readLong()
                case "bool"   => boolVal = readBool()
                case "ref"    => refVal = readInt()
                case _        => skipValue()
            case "values" =>
              attrType match
                case "stringList" => stringListVal = readStringList()
                case "intList"    => intListVal = readIntList()
                case "attrList"   => attrListVal = readAttrList()
                case "attrMap"    => attrMapVal = readAttrMapEntries()
                case _            => skipValue()
            case "mapKey" => skipValue() // handled in readAttrMapEntries
            case "attr"   => skipValue() // handled in readAttrMapEntries
            case _        => skipValue()
          if peek() == ',' then advance()

        expectChar('}')

        attrType match
          case "string"     => Attribute.StringAttr(key, stringVal)
          case "int"        => Attribute.IntAttr(key, intVal)
          case "long"       => Attribute.LongAttr(key, longVal)
          case "bool"       => Attribute.BoolAttr(key, boolVal)
          case "stringList" => Attribute.StringListAttr(key, stringListVal)
          case "intList"    => Attribute.IntListAttr(key, intListVal)
          case "attrList"   => Attribute.AttrListAttr(key, attrListVal)
          case "attrMap"    => Attribute.AttrMapAttr(key, attrMapVal.toMap)
          case "ref"        => Attribute.RefAttr(key, NodeId(refVal))
          case other        => throw IllegalArgumentException(s"Unknown attribute type: $other")

      private def readStringList(): List[String] =
        expectChar('[')
        var list = List.empty[String]
        while peek() != ']' do
          list = list :+ readString()
          if peek() == ',' then advance()
        expectChar(']')
        list

      private def readIntList(): List[Int] =
        expectChar('[')
        var list = List.empty[Int]
        while peek() != ']' do
          list = list :+ readInt()
          if peek() == ',' then advance()
        expectChar(']')
        list

      private def readAttrList(): List[Attribute] =
        expectChar('[')
        var list = List.empty[Attribute]
        while peek() != ']' do
          list = list :+ readAttr()
          if peek() == ',' then advance()
        expectChar(']')
        list

      private def readAttrMapEntries(): List[(String, Attribute)] =
        expectChar('[')
        var entries = List.empty[(String, Attribute)]
        while peek() != ']' do
          entries = entries :+ readAttrMapEntry()
          if peek() == ',' then advance()
        expectChar(']')
        entries

      private def readAttrMapEntry(): (String, Attribute) =
        expectChar('{')
        var mapKey          = ""
        var attr: Attribute = Attribute.StringAttr("", "")

        while peek() != '}' do
          val k = readString()
          expectChar(':')
          k match
            case "mapKey" => mapKey = readString()
            case "attr"   => attr = readAttr()
            case _        => skipValue()
          if peek() == ',' then advance()

        expectChar('}')
        (mapKey, attr)

      // -- Lexer primitives ---------------------------------------------------

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
        pos += 1
        sb.toString

      private def readInt(): Int =
        skipWhitespace()
        val start = pos
        if input(pos) == '-' then pos += 1
        while pos < input.length && input(pos).isDigit do pos += 1
        input.substring(start, pos).toInt

      private def readLong(): Long =
        skipWhitespace()
        val start = pos
        if input(pos) == '-' then pos += 1
        while pos < input.length && input(pos).isDigit do pos += 1
        input.substring(start, pos).toLong

      private def readBool(): Boolean =
        skipWhitespace()
        if input.startsWith("true", pos) then
          pos += 4; true
        else if input.startsWith("false", pos) then
          pos += 5; false
        else throw IllegalArgumentException(s"Expected boolean at position $pos")

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
        if input(pos) != c then throw IllegalArgumentException(s"Expected '$c' at position $pos, got '${input(pos)}'")
        pos += 1

      private def skipWhitespace(): Unit =
        while pos < input.length && input(pos).isWhitespace do pos += 1
