package io.alnovis.ircraft.core

/**
  * Prints IR as human-readable text, similar to MLIR textual format.
  *
  * Works for any Operation subclass - both typed (MessageOp, ClassOp) and generic (GenericOp). Uses only the Operation
  * trait interface: kind, attributes, regions.
  *
  * {{{
  * module "my-project" {
  *   proto.message [name="Money", presentIn=["v1","v2"]] {
  *     fields {
  *       proto.field [name="amount", number=1]
  *     }
  *   }
  * }
  * }}}
  */
object IrPrinter:

  private val Indent = "  "

  /** Print a complete IrModule. */
  def print(module: IrModule): String =
    val sb    = StringBuilder()
    val attrs = printAttrs(module.attributes)
    sb.append(s"""module "${module.name}"$attrs {\n""")
    for op <- module.topLevel do
      sb.append(printOp(op, 1))
      sb.append("\n")
    sb.append("}\n")
    sb.result()

  /** Print a single Operation. */
  def print(op: Operation): String = printOp(op, 0)

  private def printOp(op: Operation, level: Int): String =
    val prefix = Indent * level
    val name   = op.kind.qualifiedName
    val attrs  = printAttrs(op.attributes)

    if op.regions.isEmpty || op.regions.forall(_.operations.isEmpty) then
      // Leaf - single line
      s"$prefix$name$attrs"
    else
      // Container - block with regions
      val sb = StringBuilder()
      sb.append(s"$prefix$name$attrs {\n")
      for r <- op.regions if r.operations.nonEmpty do sb.append(printRegion(r, level + 1))
      sb.append(s"$prefix}")
      sb.result()

  private def printRegion(region: Region, level: Int): String =
    val prefix = Indent * level
    val sb     = StringBuilder()
    sb.append(s"$prefix${region.name} {\n")
    for op <- region.operations do
      sb.append(printOp(op, level + 1))
      sb.append("\n")
    sb.append(s"$prefix}\n")
    sb.result()

  private def printAttrs(attrs: AttributeMap): String =
    if attrs.isEmpty then ""
    else
      val entries = attrs.values.map(printAttr).toList.sorted
      s" [${entries.mkString(", ")}]"

  private def printAttr(attr: Attribute): String = attr match
    case Attribute.StringAttr(k, v)     => s"""$k="${escape(v)}""""
    case Attribute.IntAttr(k, v)        => s"$k=$v"
    case Attribute.LongAttr(k, v)       => s"$k=${v}L"
    case Attribute.BoolAttr(k, v)       => s"$k=$v"
    case Attribute.StringListAttr(k, v) => s"""$k=[${v.map(s => s""""${escape(s)}"""").mkString(",")}]"""
    case Attribute.IntListAttr(k, v)    => s"$k=[${v.mkString(",")}]"
    case Attribute.AttrListAttr(k, v)   => s"$k=[${v.map(printAttr).mkString(", ")}]"
    case Attribute.AttrMapAttr(k, v) =>
      val entries = v.map((mk, mv) => s"$mk=${printAttrValue(mv)}").toList.sorted
      s"$k={${entries.mkString(", ")}}"
    case Attribute.RefAttr(k, target) => s"$k=@${target.value}"

  private def printAttrValue(attr: Attribute): String = attr match
    case Attribute.StringAttr(_, v)     => s""""${escape(v)}""""
    case Attribute.IntAttr(_, v)        => v.toString
    case Attribute.LongAttr(_, v)       => s"${v}L"
    case Attribute.BoolAttr(_, v)       => v.toString
    case Attribute.StringListAttr(_, v) => s"""[${v.map(s => s""""${escape(s)}"""").mkString(",")}]"""
    case Attribute.IntListAttr(_, v)    => s"[${v.mkString(",")}]"
    case Attribute.RefAttr(_, t)        => s"@${t.value}"
    case _                              => attr.toString

  private def escape(s: String): String =
    s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n")
