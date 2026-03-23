package io.alnovis.ircraft.dialect.proto.lowering

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.dialect.semantic.ops.*
import io.alnovis.ircraft.dialect.semantic.expr.*

/** Enriches Semantic IR with conflict-specific methods based on proto metadata attributes.
  *
  * For each getter method with a non-None conflictType attribute, generates additional methods in the interface and
  * abstract class. Does not modify impl classes — extract methods handle type conversion.
  *
  * Reads: ProtoAttributes.ConflictType on MethodOp
  */
object ConflictResolutionPass extends Pass:

  val name: String        = "conflict-resolution"
  val description: String = "Adds conflict-specific methods (enum helpers, byte accessors, etc.)"

  def run(module: Module, context: PassContext): PassResult =
    val transformed = module.transform:
      case iface: InterfaceOp => enrichInterface(iface)
      case cls: ClassOp if cls.isAbstract => enrichAbstractClass(cls)
    PassResult(transformed)

  private def enrichInterface(iface: InterfaceOp): InterfaceOp =
    val additionalMethods = iface.methods.flatMap(conflictMethodsForInterface)
    if additionalMethods.isEmpty then iface
    else
      InterfaceOp(
        name = iface.name,
        modifiers = iface.modifiers,
        typeParams = iface.typeParams,
        extendsTypes = iface.extendsTypes,
        methods = iface.methods ++ additionalMethods,
        nestedTypes = iface.nestedTypes,
        javadoc = iface.javadoc,
        annotations = iface.annotations,
        attributes = iface.attributes,
        span = iface.span
      )

  private def enrichAbstractClass(cls: ClassOp): ClassOp =
    val additionalMethods = cls.methods.flatMap(conflictMethodsForAbstract)
    if additionalMethods.isEmpty then cls
    else
      ClassOp(
        name = cls.name,
        modifiers = cls.modifiers,
        typeParams = cls.typeParams,
        superClass = cls.superClass,
        implementsTypes = cls.implementsTypes,
        fields = cls.fields,
        constructors = cls.constructors,
        methods = cls.methods ++ additionalMethods,
        nestedTypes = cls.nestedTypes,
        javadoc = cls.javadoc,
        annotations = cls.annotations,
        attributes = cls.attributes,
        span = cls.span
      )

  // ── Conflict method generation ─────────────────────────────────────────

  private def conflictMethodsForInterface(m: MethodOp): Vector[MethodOp] =
    val ct = m.attributes.getString(ProtoAttributes.ConflictType).getOrElse("None")
    val fieldName = extractFieldName(m.name)
    ct match
      case "IntEnum" =>
        Vector(MethodOp(
          s"get${fieldName}Enum",
          TypeRef.NamedType(s"${fieldName}Enum"),
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some(s"Returns the ${fieldName} value as enum (for INT_ENUM conflict).")
        ))
      case "StringBytes" =>
        Vector(MethodOp(
          s"get${fieldName}Bytes",
          TypeRef.BYTES,
          modifiers = Set(Modifier.Public, Modifier.Abstract),
          javadoc = Some(s"Returns the ${fieldName} value as bytes (for STRING_BYTES conflict).")
        ))
      case "PrimitiveMessage" =>
        Vector(
          MethodOp(
            s"get${fieldName}Message",
            TypeRef.NamedType("com.google.protobuf.Message"),
            modifiers = Set(Modifier.Public, Modifier.Abstract),
            javadoc = Some(s"Returns the ${fieldName} value as message (for PRIMITIVE_MESSAGE conflict).")
          ),
          MethodOp(
            s"supports${fieldName}Message",
            TypeRef.BOOL,
            modifiers = Set(Modifier.Public, Modifier.Abstract),
            javadoc = Some(s"Returns true if this version supports ${fieldName} as message type.")
          )
        )
      case _ => Vector.empty

  private def conflictMethodsForAbstract(m: MethodOp): Vector[MethodOp] =
    val ct = m.attributes.getString(ProtoAttributes.ConflictType).getOrElse("None")
    val fieldName = extractFieldName(m.name)
    ct match
      case "IntEnum" =>
        Vector(
          MethodOp(
            s"extract${fieldName}Enum",
            TypeRef.NamedType(s"${fieldName}Enum"),
            modifiers = Set(Modifier.Protected, Modifier.Abstract)
          ),
          MethodOp(
            s"get${fieldName}Enum",
            TypeRef.NamedType(s"${fieldName}Enum"),
            modifiers = Set(Modifier.Public, Modifier.Override),
            body = Some(Block.of(
              Statement.ReturnStmt(Some(Expression.MethodCall(None, s"extract${fieldName}Enum")))
            ))
          )
        )
      case "StringBytes" =>
        Vector(
          MethodOp(
            s"extract${fieldName}Bytes",
            TypeRef.BYTES,
            modifiers = Set(Modifier.Protected, Modifier.Abstract)
          ),
          MethodOp(
            s"get${fieldName}Bytes",
            TypeRef.BYTES,
            modifiers = Set(Modifier.Public, Modifier.Override),
            body = Some(Block.of(
              Statement.ReturnStmt(Some(Expression.MethodCall(None, s"extract${fieldName}Bytes")))
            ))
          )
        )
      case "PrimitiveMessage" =>
        Vector(
          MethodOp(
            s"extract${fieldName}Message",
            TypeRef.NamedType("com.google.protobuf.Message"),
            modifiers = Set(Modifier.Protected, Modifier.Abstract)
          ),
          MethodOp(
            s"get${fieldName}Message",
            TypeRef.NamedType("com.google.protobuf.Message"),
            modifiers = Set(Modifier.Public, Modifier.Override),
            body = Some(Block.of(
              Statement.ReturnStmt(Some(Expression.MethodCall(None, s"extract${fieldName}Message")))
            ))
          ),
          MethodOp(
            s"supports${fieldName}Message",
            TypeRef.BOOL,
            modifiers = Set(Modifier.Public, Modifier.Abstract)
          )
        )
      case _ => Vector.empty

  /** Extract field name from getter name: "getAmount" → "Amount", "extractAmount" → "Amount" */
  private def extractFieldName(methodName: String): String =
    if methodName.startsWith("get") then methodName.drop(3)
    else if methodName.startsWith("extract") then methodName.drop(7)
    else methodName.capitalize
