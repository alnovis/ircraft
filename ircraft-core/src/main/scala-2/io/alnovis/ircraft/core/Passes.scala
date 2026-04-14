package io.alnovis.ircraft.core

import cats._
import cats.data.{IorT, NonEmptyChain}
import io.alnovis.ircraft.core.algebra.{Fix, scheme}
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._
import io.alnovis.ircraft.core.ir.TypeExpr.TypeExprOps

/**
  * Built-in validation and analysis passes for the ircraft core IR.
  *
  * This object provides standard passes that operate on `Module[Fix[SemanticF]]`,
  * primarily for detecting unresolved type references after type resolution.
  *
  * @see [[Pass]] for the pass abstraction (Kleisli-based)
  * @see [[Outcome]] for the IorT-based result type used by passes
  * @see [[io.alnovis.ircraft.core.algebra.scheme]] for the catamorphism used internally
  */
object Passes {

  /** Type alias for a non-empty chain of [[Diagnostic]] messages. */
  private type Diags = NonEmptyChain[Diagnostic]

  /**
    * An F-algebra that collects all [[TypeExpr.Unresolved]] type references
    * found within [[SemanticF]] nodes.
    *
    * For each unresolved reference, a tuple of `(declarationName, memberName, fqn)`
    * is produced, where `fqn` is the unresolved fully qualified name.
    *
    * @return a vector of `(declarationName, memberName, unresolvedFqn)` tuples
    * @see [[io.alnovis.ircraft.core.algebra.Algebra.Algebra]]
    */
  val findUnresolvedAlg: Algebra[SemanticF, Vector[(String, String, String)]] = {
    case TypeDeclF(name, _, fields, functions, nested, _, _, _, _, _) =>
      fields.flatMap(f => findInType(f.fieldType).map(fqn => (name, f.name, fqn))) ++
        functions.flatMap(f => findInFunc(name, f)) ++
        nested.flatten
    case EnumDeclF(name, _, functions, _, _, _, _) =>
      functions.flatMap(f => findInFunc(name, f))
    case _ => Vector.empty
  }

  /**
    * A pass that validates all type references in the module have been resolved.
    *
    * If any [[TypeExpr.Unresolved]] references remain, the pass emits error
    * diagnostics. Otherwise, the module is returned unchanged.
    *
    * @tparam F the effect type (must have an [[cats.Applicative]] instance)
    * @return a [[Pass]] that validates type resolution completeness
    * @see [[Diagnostic]]
    * @see [[Severity.Error]]
    */
  def validateResolved[F[_]: Applicative]: Pass[({type L[A] = IorT[F, Diags, A]})#L] =
    Pass[({type L[A] = IorT[F, Diags, A]})#L]("validate-resolved") { module =>
      val findUnresolved = scheme.cata(findUnresolvedAlg)
      val errors = module.units.flatMap { unit =>
        unit.declarations.flatMap(findUnresolved(_))
      }
      val diags = errors.map { case (declName, memberName, fqn) =>
        Diagnostic(Severity.Error, s"Unresolved type '$fqn' in $declName.$memberName")
      }
      NonEmptyChain.fromSeq(diags) match {
        case Some(nec) => Outcome.failNec(nec)
        case None      => Outcome.ok(module)
      }
    }

  /**
    * Finds unresolved type references within a function's return type and parameters.
    *
    * @param declName the name of the enclosing declaration (for diagnostic context)
    * @param f        the function to inspect
    * @return a vector of `(declarationName, memberName, unresolvedFqn)` tuples
    */
  private def findInFunc(declName: String, f: io.alnovis.ircraft.core.ir.Func): Vector[(String, String, String)] =
    findInType(f.returnType).map(fqn => (declName, f.name, fqn)) ++
      f.params.flatMap(p => findInType(p.paramType).map(fqn => (declName, s"${f.name}.${p.name}", fqn)))

  /**
    * Collects all [[TypeExpr.Unresolved]] FQNs within a type expression.
    *
    * @param t the type expression to search
    * @return a vector of unresolved fully qualified names
    */
  private def findInType(t: TypeExpr): Vector[String] =
    t.foldMap {
      case TypeExpr.Unresolved(fqn) => Vector(fqn)
      case _ => Vector.empty
    }
}
