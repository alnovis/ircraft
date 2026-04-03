package io.alnovis.ircraft.dialects.proto

import cats.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.*

/** Well-known Meta keys for proto provenance. */
object ProtoMeta:
  val sourceKind: Meta.Key[String]    = Meta.Key("proto.sourceKind")
  val fieldNumber: Meta.Key[Int]      = Meta.Key("proto.fieldNumber")
  val protoFqn: Meta.Key[String]      = Meta.Key("proto.fqn")
  val protoPackage: Meta.Key[String]  = Meta.Key("proto.package")
  val outerClass: Meta.Key[String]    = Meta.Key("proto.outerClass")
  val syntax: Meta.Key[String]        = Meta.Key("proto.syntax")
  val fieldKind: Meta.Key[String]     = Meta.Key("proto.fieldKind")
  val originalType: Meta.Key[String]  = Meta.Key("proto.originalType")

/** Lowering: ProtoFile -> F[Module]. */
object ProtoLowering:

  def lower[F[_]: Applicative](file: ProtoFile): F[Module] =
    lowerFile(file)

  def lowerAll[F[_]: Applicative](files: Vector[ProtoFile]): F[Module] =
    files.traverse(lowerFile[F]).map(_.combineAll)

  private def lowerFile[F[_]: Applicative](file: ProtoFile): F[Module] =
    val pkg = file.javaPackage.getOrElse(file.packageName)
    val outerClass = file.javaOuterClassname.getOrElse(deriveOuterClass(file.name))
    val syntaxStr = file.syntax match
      case ProtoSyntax.Proto2 => "proto2"
      case ProtoSyntax.Proto3 => "proto3"

    val messageDecls = file.messages.map(m => lowerMessage(m, pkg, outerClass, file.syntax))
    val enumDecls = file.enums.map(lowerEnum)

    val unit = CompilationUnit(
      namespace = pkg,
      declarations = messageDecls ++ enumDecls,
      meta = Meta.empty
        .set(ProtoMeta.protoPackage, file.packageName)
        .set(ProtoMeta.outerClass, outerClass)
        .set(ProtoMeta.syntax, syntaxStr)
    )
    Applicative[F].pure(Module(file.name, Vector(unit)))

  private def lowerMessage(
    msg: ProtoMessage,
    pkg: String,
    outerClass: String,
    syntax: ProtoSyntax
  ): Decl =
    val protoFqn = s"$pkg.$outerClass.${msg.name}"

    val fields = msg.fields.map(f => lowerField(f, syntax))

    // getter functions
    val getters = msg.fields.map { f =>
      val getterName = s"get${capitalize(f.name)}"
      val returnType = lowerType(f.fieldType, f.label)
      val kind = classifyFieldKind(f)
      Func(
        name = getterName,
        returnType = returnType,
        meta = Meta.empty
          .set(ProtoMeta.fieldNumber, f.number)
          .set(ProtoMeta.fieldKind, kind)
          .set(ProtoMeta.originalType, f.typeName.getOrElse(""))
      )
    }

    // has-methods for proto2 optional/required and proto3 optional message fields
    val hasMethods = msg.fields.filter(needsHasMethod(_, syntax)).map { f =>
      Func(
        name = s"has${capitalize(f.name)}",
        returnType = TypeExpr.BOOL
      )
    }

    // nested
    val nestedMessages = msg.nestedMessages.map(m => lowerMessage(m, pkg, outerClass, syntax))
    val nestedEnums = msg.nestedEnums.map(lowerEnum)

    Decl.TypeDecl(
      name = msg.name,
      kind = TypeKind.Protocol,
      fields = fields,
      functions = getters ++ hasMethods,
      nested = nestedMessages ++ nestedEnums,
      meta = Meta.empty
        .set(ProtoMeta.sourceKind, "message")
        .set(ProtoMeta.protoFqn, protoFqn)
    )

  private def lowerField(f: ProtoField, @scala.annotation.unused syntax: ProtoSyntax): Field =
    val fieldType = lowerType(f.fieldType, f.label)
    val mutability = Mutability.Immutable
    Field(
      name = f.name,
      fieldType = fieldType,
      mutability = mutability,
      meta = Meta.empty.set(ProtoMeta.fieldNumber, f.number)
    )

  private def lowerEnum(e: ProtoEnum): Decl =
    Decl.EnumDecl(
      name = e.name,
      variants = e.values.map(v => EnumVariant(v.name, Vector(Expr.Lit(v.number.toString, TypeExpr.INT)))),
      meta = Meta.empty.set(ProtoMeta.sourceKind, "enum")
    )

  private def lowerType(pt: ProtoType, label: ProtoLabel): TypeExpr =
    val base = scalarType(pt)
    label match
      case ProtoLabel.Repeated =>
        pt match
          case ProtoType.Map(k, v) => TypeExpr.MapOf(scalarType(k), scalarType(v))
          case _                   => TypeExpr.ListOf(base)
      case ProtoLabel.Optional => TypeExpr.Optional(base)
      case ProtoLabel.Required => base

  private def scalarType(pt: ProtoType): TypeExpr = pt match
    case ProtoType.Double   => TypeExpr.DOUBLE
    case ProtoType.Float    => TypeExpr.FLOAT
    case ProtoType.Int32    => TypeExpr.INT
    case ProtoType.Int64    => TypeExpr.LONG
    case ProtoType.UInt32   => TypeExpr.Primitive.UInt32
    case ProtoType.UInt64   => TypeExpr.Primitive.UInt64
    case ProtoType.SInt32   => TypeExpr.Primitive.Int32
    case ProtoType.SInt64   => TypeExpr.Primitive.Int64
    case ProtoType.Fixed32  => TypeExpr.Primitive.UInt32
    case ProtoType.Fixed64  => TypeExpr.Primitive.UInt64
    case ProtoType.SFixed32 => TypeExpr.Primitive.Int32
    case ProtoType.SFixed64 => TypeExpr.Primitive.Int64
    case ProtoType.Bool     => TypeExpr.BOOL
    case ProtoType.String   => TypeExpr.STR
    case ProtoType.Bytes    => TypeExpr.BYTES
    case ProtoType.Message(fqn) => TypeExpr.Unresolved(fqn)
    case ProtoType.Enum(fqn)    => TypeExpr.Unresolved(fqn)
    case ProtoType.Map(k, v)    => TypeExpr.MapOf(scalarType(k), scalarType(v))

  private def classifyFieldKind(f: ProtoField): String =
    f.label match
      case ProtoLabel.Repeated =>
        f.fieldType match
          case ProtoType.Map(_, _)  => "MAP"
          case _: ProtoType.Message => "REPEATED_MESSAGE"
          case _: ProtoType.Enum   => "REPEATED_ENUM"
          case _                    => "REPEATED_SCALAR"
      case _ =>
        f.fieldType match
          case _: ProtoType.Message => "MESSAGE"
          case _: ProtoType.Enum   => "ENUM"
          case _                    => "SCALAR"

  private def needsHasMethod(f: ProtoField, @scala.annotation.unused syntax: ProtoSyntax): Boolean =
    f.label == ProtoLabel.Optional

  private def capitalize(s: String): String =
    if s.isEmpty then s else s.head.toUpper +: s.tail

  private def deriveOuterClass(fileName: String): String =
    val base = fileName.stripSuffix(".proto")
    base.split("[_\\-]").map(capitalize).mkString
