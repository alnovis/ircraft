package io.alnovis.ircraft.dialect.semantic.expr

import io.alnovis.ircraft.core.{ ContentHash, ContentHashable, TypeRef }

/** Language-agnostic code statement. */
sealed trait Statement

object Statement:
  case class ExpressionStmt(expr: Expression)             extends Statement
  case class ReturnStmt(value: Option[Expression] = None) extends Statement

  case class VarDecl(name: String, varType: TypeRef, initializer: Option[Expression] = None, isFinal: Boolean = false)
      extends Statement
  case class Assignment(target: Expression, value: Expression)                                  extends Statement
  case class IfStmt(cond: Expression, thenBlock: Block, elseBlock: Option[Block] = None)        extends Statement
  case class ForEachStmt(variable: String, varType: TypeRef, iterable: Expression, body: Block) extends Statement
  case class ThrowStmt(expr: Expression)                                                        extends Statement

  case class TryCatchStmt(tryBlock: Block, catches: List[CatchClause], finallyBlock: Option[Block] = None)
      extends Statement

  given ContentHashable[Statement] with

    def contentHash(a: Statement): Int =
      val exprHash    = summon[ContentHashable[Expression]]
      val typeRefHash = summon[ContentHashable[TypeRef]]
      val blockHash   = summon[ContentHashable[Block]]
      a match
        case ExpressionStmt(expr) => ContentHash.combine(1, exprHash.contentHash(expr))
        case ReturnStmt(value)    => ContentHash.combine(2, value.map(exprHash.contentHash).getOrElse(0))
        case VarDecl(n, t, init, fin) =>
          ContentHash.combine(
            3,
            ContentHash.ofString(n),
            typeRefHash.contentHash(t),
            init.map(exprHash.contentHash).getOrElse(0),
            ContentHash.ofBoolean(fin)
          )
        case Assignment(target, value) =>
          ContentHash.combine(4, exprHash.contentHash(target), exprHash.contentHash(value))
        case IfStmt(c, t, e) =>
          ContentHash.combine(
            5,
            exprHash.contentHash(c),
            blockHash.contentHash(t),
            e.map(blockHash.contentHash).getOrElse(0)
          )
        case ForEachStmt(v, t, it, b) =>
          ContentHash.combine(
            6,
            ContentHash.ofString(v),
            typeRefHash.contentHash(t),
            exprHash.contentHash(it),
            blockHash.contentHash(b)
          )
        case ThrowStmt(expr) => ContentHash.combine(7, exprHash.contentHash(expr))
        case TryCatchStmt(t, cs, f) =>
          ContentHash.combine(
            8,
            blockHash.contentHash(t),
            ContentHash.ofList(cs)(using summon[ContentHashable[CatchClause]]),
            f.map(blockHash.contentHash).getOrElse(0)
          )

/** A sequence of statements. */
case class Block(statements: List[Statement])

object Block:
  val empty: Block = Block(Nil)

  def of(stmts: Statement*): Block = Block(stmts.toList)

  given ContentHashable[Block] with

    def contentHash(a: Block): Int =
      ContentHash.ofList(a.statements)(using summon[ContentHashable[Statement]])

/** Catch clause for try-catch statements. */
case class CatchClause(
  exceptionType: TypeRef,
  variableName: String,
  body: Block
)

object CatchClause:

  given ContentHashable[CatchClause] with

    def contentHash(a: CatchClause): Int =
      ContentHash.combine(
        summon[ContentHashable[TypeRef]].contentHash(a.exceptionType),
        ContentHash.ofString(a.variableName),
        summon[ContentHashable[Block]].contentHash(a.body)
      )
