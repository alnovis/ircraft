package io.alnovis.ircraft.dialects.proto

import cats._
import cats.syntax.all._
import io.alnovis.ircraft.core.algebra.{ Coproduct, Fix, Inject }
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.dialects.proto.ProtoF._

/**
  * Well-known `Meta` keys for protobuf provenance information.
  *
  * These keys are attached to IR nodes during proto lowering to preserve
  * information about the original `.proto` source. Downstream passes and
  * emitters can use these keys to make decisions based on proto semantics.
  *
  * @see [[ProtoLowering]] for where these keys are set
  */
object ProtoMeta {

  /** The kind of proto source node (`"message"` or `"enum"`). */
  val sourceKind: Meta.Key[String] = Meta.Key("proto.sourceKind")

  /** The proto field number, attached to getter functions and fields. */
  val fieldNumber: Meta.Key[Int] = Meta.Key("proto.fieldNumber")

  /** The fully qualified proto name (e.g., `"com.example.OuterClass.MessageName"`). */
  val protoFqn: Meta.Key[String] = Meta.Key("proto.fqn")

  /** The proto package name (from the `package` directive). */
  val protoPackage: Meta.Key[String] = Meta.Key("proto.package")

  /** The outer class name (from `java_outer_classname` or derived from the file name). */
  val outerClass: Meta.Key[String] = Meta.Key("proto.outerClass")

  /** The proto syntax version (`"proto2"` or `"proto3"`). */
  val syntax: Meta.Key[String] = Meta.Key("proto.syntax")

  /**
    * The field kind classification string.
    *
    * Possible values: `"SCALAR"`, `"MESSAGE"`, `"ENUM"`, `"MAP"`,
    * `"REPEATED_SCALAR"`, `"REPEATED_MESSAGE"`, `"REPEATED_ENUM"`.
    */
  val fieldKind: Meta.Key[String] = Meta.Key("proto.fieldKind")

  /** The original proto type name from the `.proto` file. */
  val originalType: Meta.Key[String] = Meta.Key("proto.originalType")
}

/**
  * Lowers [[ProtoFile]] instances into the semantic IR (`Module[Fix[SemanticF]]`).
  *
  * Provides two lowering paths:
  *  - '''Pure lowering''' via [[lower]] / [[lowerAll]]: converts proto constructs directly
  *    to `SemanticF` nodes. Messages become `TypeDeclF(Protocol)`, enums become `EnumDeclF`.
  *  - '''Mixed lowering''' via [[lowerToMixed]]: preserves proto-specific nodes as `ProtoF`
  *    within a coproduct IR (`ProtoF :+: SemanticF`), allowing dialect-specific passes
  *    before final elimination.
  *
  * Both paths attach [[ProtoMeta]] keys to IR nodes for provenance tracking.
  *
  * @see [[ProtoMeta]] for the metadata keys attached during lowering
  * @see [[ProtoDialect]] for the algebra-based elimination of `ProtoF` nodes
  */
object ProtoLowering {

  /**
    * Lowers a single [[ProtoFile]] to a pure semantic IR module.
    *
    * Each message is lowered to a `TypeDeclF(Protocol)` with getter functions for fields.
    * Each enum is lowered to an `EnumDeclF` with numeric-valued variants.
    * The resulting module contains one compilation unit with the file's Java package
    * as namespace.
    *
    * @tparam F the effect type (only `Applicative` is required)
    * @param file the proto file to lower
    * @return a module containing the lowered declarations
    */
  def lower[F[_]: Applicative](file: ProtoFile): F[Module[Fix[SemanticF]]] =
    lowerFile(file)

  /**
    * Lowers multiple [[ProtoFile]] instances and combines them into a single module.
    *
    * Each file is lowered independently via [[lower]], and the resulting modules are
    * combined using the `Module` monoid (merging compilation units).
    *
    * @tparam F the effect type (only `Applicative` is required)
    * @param files the proto files to lower
    * @return a combined module containing all lowered declarations
    */
  def lowerAll[F[_]: Applicative](files: Vector[ProtoFile]): F[Module[Fix[SemanticF]]] =
    files.toList.traverse(lowerFile[F](_)).map(_.toVector.combineAll)

  /**
    * Lowers a [[ProtoFile]] to a mixed IR that preserves proto-specific `ProtoF` nodes.
    *
    * Proto constructs are injected into the IR via the `Inject[ProtoF, IR]` type class,
    * producing an IR that is typically `ProtoF :+: SemanticF` (Scala 3) or
    * `Coproduct[ProtoF, SemanticF, *]` (Scala 2).
    *
    * Use `ProtoEliminate.eliminateProto` to convert the mixed IR to pure `SemanticF`
    * after any proto-specific passes.
    *
    * @tparam F  the effect type (only `Applicative` is required)
    * @tparam IR the coproduct IR functor (e.g., `ProtoF :+: SemanticF`)
    * @param file the proto file to lower
    * @param injP the injection evidence for `ProtoF` into `IR`
    * @return a module with proto-specific nodes preserved in the coproduct IR
    *
    * @see [[ProtoDialect.protoToSemantic]] for the elimination algebra
    */
  def lowerToMixed[F[_]: Applicative, IR[_]](file: ProtoFile)(implicit injP: Inject[ProtoF, IR]): F[Module[Fix[IR]]] = {
    val pkg        = file.javaPackage.getOrElse(file.packageName)
    val outerClass = file.javaOuterClassname.getOrElse(deriveOuterClass(file.name))
    val syntaxStr = file.syntax match {
      case ProtoSyntax.Proto2 => "proto2"
      case ProtoSyntax.Proto3 => "proto3"
    }

    def mixedMessage(msg: ProtoMessage): Fix[IR] = {
      val protoFqn = s"$pkg.$outerClass.${msg.name}"
      val fields   = msg.fields.map(f => lowerField(f, file.syntax))
      val getters = msg.fields.map { f =>
        val getterName = s"get${capitalize(f.name)}"
        val returnType = lowerType(f.fieldType, f.label)
        val kind       = classifyFieldKind(f)
        Func(
          name = getterName,
          returnType = returnType,
          meta = Meta.empty
            .set(ProtoMeta.fieldNumber, f.number)
            .set(ProtoMeta.fieldKind, kind)
            .set(ProtoMeta.originalType, f.typeName.getOrElse(""))
        )
      }
      val hasMethods = msg.fields.filter(needsHasMethod(_, file.syntax)).map { f =>
        Func(name = s"has${capitalize(f.name)}", returnType = TypeExpr.BOOL)
      }
      val nestedMessages = msg.nestedMessages.map(mixedMessage)
      val nestedEnums    = msg.nestedEnums.map(mixedEnum)

      Fix(
        injP.inj(
          MessageNodeF[Fix[IR]](
            name = msg.name,
            fields = fields,
            functions = getters ++ hasMethods,
            nested = nestedMessages ++ nestedEnums,
            meta = Meta.empty
              .set(ProtoMeta.sourceKind, "message")
              .set(ProtoMeta.protoFqn, protoFqn)
          )
        )
      )
    }

    def mixedEnum(e: ProtoEnum): Fix[IR] =
      Fix(
        injP.inj(
          EnumNodeF[Fix[IR]](
            name = e.name,
            variants = e.values.map(v => EnumVariant(v.name, Vector(Expr.Lit(v.number.toString, TypeExpr.INT)))),
            meta = Meta.empty.set(ProtoMeta.sourceKind, "enum")
          )
        )
      )

    val messageDecls = file.messages.map(mixedMessage)
    val enumDecls    = file.enums.map(mixedEnum)

    val unit = CompilationUnit(
      namespace = pkg,
      declarations = messageDecls ++ enumDecls,
      meta = Meta.empty
        .set(ProtoMeta.protoPackage, file.packageName)
        .set(ProtoMeta.outerClass, outerClass)
        .set(ProtoMeta.syntax, syntaxStr)
    )
    Applicative[F].pure(Module(file.name, Vector(unit)))
  }

  private def lowerFile[F[_]: Applicative](file: ProtoFile): F[Module[Fix[SemanticF]]] = {
    val pkg        = file.javaPackage.getOrElse(file.packageName)
    val outerClass = file.javaOuterClassname.getOrElse(deriveOuterClass(file.name))
    val syntaxStr = file.syntax match {
      case ProtoSyntax.Proto2 => "proto2"
      case ProtoSyntax.Proto3 => "proto3"
    }

    val messageDecls = file.messages.map(m => lowerMessage(m, pkg, outerClass, file.syntax))
    val enumDecls    = file.enums.map(lowerEnum)

    val unit = CompilationUnit(
      namespace = pkg,
      declarations = messageDecls ++ enumDecls,
      meta = Meta.empty
        .set(ProtoMeta.protoPackage, file.packageName)
        .set(ProtoMeta.outerClass, outerClass)
        .set(ProtoMeta.syntax, syntaxStr)
    )
    Applicative[F].pure(Module(file.name, Vector(unit)))
  }

  private def lowerMessage(
    msg: ProtoMessage,
    pkg: String,
    outerClass: String,
    syntax: ProtoSyntax
  ): Fix[SemanticF] = {
    val protoFqn = s"$pkg.$outerClass.${msg.name}"

    val fields = msg.fields.map(f => lowerField(f, syntax))

    // getter functions
    val getters = msg.fields.map { f =>
      val getterName = s"get${capitalize(f.name)}"
      val returnType = lowerType(f.fieldType, f.label)
      val kind       = classifyFieldKind(f)
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
    val nestedEnums    = msg.nestedEnums.map(lowerEnum)

    Decl.typeDecl(
      name = msg.name,
      kind = TypeKind.Protocol,
      fields = fields,
      functions = getters ++ hasMethods,
      nested = nestedMessages ++ nestedEnums,
      meta = Meta.empty
        .set(ProtoMeta.sourceKind, "message")
        .set(ProtoMeta.protoFqn, protoFqn)
    )
  }

  private def lowerField(f: ProtoField, @scala.annotation.unused syntax: ProtoSyntax): Field = {
    val fieldType  = lowerType(f.fieldType, f.label)
    val mutability = Mutability.Immutable
    Field(
      name = f.name,
      fieldType = fieldType,
      mutability = mutability,
      meta = Meta.empty.set(ProtoMeta.fieldNumber, f.number)
    )
  }

  private def lowerEnum(e: ProtoEnum): Fix[SemanticF] =
    Decl.enumDecl(
      name = e.name,
      variants = e.values.map(v => EnumVariant(v.name, Vector(Expr.Lit(v.number.toString, TypeExpr.INT)))),
      meta = Meta.empty.set(ProtoMeta.sourceKind, "enum")
    )

  private def lowerType(pt: ProtoType, label: ProtoLabel): TypeExpr = {
    val base = scalarType(pt)
    label match {
      case ProtoLabel.Repeated =>
        pt match {
          case ProtoType.Map(k, v) => TypeExpr.MapOf(scalarType(k), scalarType(v))
          case _                   => TypeExpr.ListOf(base)
        }
      case ProtoLabel.Optional => TypeExpr.Optional(base)
      case ProtoLabel.Required => base
    }
  }

  private def scalarType(pt: ProtoType): TypeExpr = pt match {
    case ProtoType.Double       => TypeExpr.DOUBLE
    case ProtoType.Float        => TypeExpr.FLOAT
    case ProtoType.Int32        => TypeExpr.INT
    case ProtoType.Int64        => TypeExpr.LONG
    case ProtoType.UInt32       => TypeExpr.Primitive.UInt32
    case ProtoType.UInt64       => TypeExpr.Primitive.UInt64
    case ProtoType.SInt32       => TypeExpr.Primitive.Int32
    case ProtoType.SInt64       => TypeExpr.Primitive.Int64
    case ProtoType.Fixed32      => TypeExpr.Primitive.UInt32
    case ProtoType.Fixed64      => TypeExpr.Primitive.UInt64
    case ProtoType.SFixed32     => TypeExpr.Primitive.Int32
    case ProtoType.SFixed64     => TypeExpr.Primitive.Int64
    case ProtoType.Bool         => TypeExpr.BOOL
    case ProtoType.String       => TypeExpr.STR
    case ProtoType.Bytes        => TypeExpr.BYTES
    case ProtoType.Message(fqn) => TypeExpr.Unresolved(fqn)
    case ProtoType.Enum(fqn)    => TypeExpr.Unresolved(fqn)
    case ProtoType.Map(k, v)    => TypeExpr.MapOf(scalarType(k), scalarType(v))
  }

  private def classifyFieldKind(f: ProtoField): String =
    f.label match {
      case ProtoLabel.Repeated =>
        f.fieldType match {
          case ProtoType.Map(_, _)  => "MAP"
          case _: ProtoType.Message => "REPEATED_MESSAGE"
          case _: ProtoType.Enum    => "REPEATED_ENUM"
          case _                    => "REPEATED_SCALAR"
        }
      case _ =>
        f.fieldType match {
          case _: ProtoType.Message => "MESSAGE"
          case _: ProtoType.Enum    => "ENUM"
          case _                    => "SCALAR"
        }
    }

  private def needsHasMethod(f: ProtoField, @scala.annotation.unused syntax: ProtoSyntax): Boolean =
    f.label == ProtoLabel.Optional

  private def capitalize(s: String): String =
    if (s.isEmpty) s else s.head.toUpper +: s.tail

  private def deriveOuterClass(fileName: String): String = {
    val base = fileName.stripSuffix(".proto")
    base.split("[_\\-]").map(capitalize).mkString
  }
}
