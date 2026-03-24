package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*

/**
  * Converts Well-Known Type fields to Java standard types.
  *
  * Transforms getter return types in interfaces and abstract classes:
  *   - Timestamp → java.time.Instant
  *   - Duration → java.time.Duration
  *   - Struct → java.util.Map<String, Object>
  *   - Value → Object
  *   - ListValue → java.util.List<Object>
  *   - Int32Value → Integer (nullable)
  *   - StringValue → String (nullable)
  *   - etc.
  *
  * Controlled by LoweringConfig.convertWellKnownTypes.
  */
object WktConversionPass extends Pass:

  val name: String        = "wkt-conversion"
  val description: String = "Converts Well-Known Type fields to Java standard types"

  override def isEnabled(context: PassContext): Boolean =
    !context.getBool("skipWktConversion")

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case m: MethodOp if hasWkt(m) => convertMethodType(m)
    PassResult(transformed)

  private def hasWkt(m: MethodOp): Boolean =
    m.attributes.getString(ProtoAttributes.WellKnownType).exists(_.nonEmpty)

  private def convertMethodType(m: MethodOp): MethodOp =
    val wkt     = m.attributes.getString(ProtoAttributes.WellKnownType).getOrElse("")
    val newType = wktToJavaType(wkt)
    if newType == m.returnType then m
    else m.copy(returnType = newType)

  private def wktToJavaType(wkt: String): TypeRef = wkt match
    case "Timestamp"   => TypeRef.NamedType("java.time.Instant")
    case "Duration"    => TypeRef.NamedType("java.time.Duration")
    case "Struct"      => TypeRef.MapType(TypeRef.STRING, TypeRef.NamedType("Object"))
    case "Value"       => TypeRef.NamedType("Object")
    case "ListValue"   => TypeRef.ListType(TypeRef.NamedType("Object"))
    case "Int32Value"  => TypeRef.NamedType("Integer")
    case "Int64Value"  => TypeRef.NamedType("Long")
    case "UInt32Value" => TypeRef.NamedType("Integer")
    case "UInt64Value" => TypeRef.NamedType("Long")
    case "FloatValue"  => TypeRef.NamedType("Float")
    case "DoubleValue" => TypeRef.NamedType("Double")
    case "BoolValue"   => TypeRef.NamedType("Boolean")
    case "StringValue" => TypeRef.STRING
    case "BytesValue"  => TypeRef.BYTES
    case _             => TypeRef.NamedType("Object")
