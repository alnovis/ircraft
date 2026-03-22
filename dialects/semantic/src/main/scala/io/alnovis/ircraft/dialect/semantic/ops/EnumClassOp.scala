package io.alnovis.ircraft.dialect.semantic.ops

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.dialect.semantic.SemanticDialect
import io.alnovis.ircraft.dialect.semantic.expr.Expression

/** Enum class declaration. */
case class EnumClassOp(
    name: String,
    modifiers: Set[Modifier] = Set(Modifier.Public),
    implementsTypes: List[TypeRef] = Nil,
    constants: Vector[EnumConstantOp] = Vector.empty,
    fields: Vector[FieldDeclOp] = Vector.empty,
    constructors: Vector[ConstructorOp] = Vector.empty,
    methods: Vector[MethodOp] = Vector.empty,
    javadoc: Option[String] = None,
    annotations: List[String] = Nil,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind = SemanticDialect.Kinds.EnumClass

  val regions: Vector[Region] = Vector(
    Region("constants", constants),
    Region("fields", fields),
    Region("constructors", constructors),
    Region("methods", methods),
  )

  lazy val contentHash: Int =
    ContentHash.combine(
      ContentHash.ofString(name),
      ContentHash.ofSet(modifiers),
      ContentHash.ofList(constants.toList)(using Operation.operationHashable),
      ContentHash.ofList(methods.toList)(using Operation.operationHashable),
    )

  lazy val width: Int = 1 + constants.size + methods.map(_.width).sum

/** Enum constant (e.g., OK(0), ERROR(1)). */
case class EnumConstantOp(
    name: String,
    arguments: List[Expression] = Nil,
    javadoc: Option[String] = None,
    attributes: AttributeMap = AttributeMap.empty,
    span: Option[Span] = None,
) extends Operation:

  val kind: NodeKind          = SemanticDialect.Kinds.EnumConstant
  val regions: Vector[Region] = Vector.empty

  lazy val contentHash: Int = ContentHash.ofString(name)

  val width: Int = 1
