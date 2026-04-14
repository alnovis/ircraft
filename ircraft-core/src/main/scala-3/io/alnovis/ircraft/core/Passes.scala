package io.alnovis.ircraft.core

import cats._
import cats.data.NonEmptyChain
import io.alnovis.ircraft.core.algebra.{ Fix, scheme }
import io.alnovis.ircraft.core.algebra.Algebra._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/**
  * Built-in validation passes for the ircraft IR pipeline.
  *
  * Provides algebras and pass combinators for common validations on
  * [[io.alnovis.ircraft.core.ir.SemanticModule]] structures, such as
  * checking that all type references have been resolved.
  *
  * @see [[io.alnovis.ircraft.core.Pass]] for the pass abstraction (Kleisli-based)
  * @see [[io.alnovis.ircraft.core.Outcome]] for the error/warning monad
  */
object Passes:

  /**
    * An F-algebra that collects [[TypeExpr.Unresolved]] types from a [[SemanticF]] tree.
    *
    * Each unresolved type is reported as a triple `(declName, memberName, unresolvedFqn)`.
    * This algebra is designed to be used with [[io.alnovis.ircraft.core.algebra.scheme.cata]]
    * for stack-safe bottom-up traversal.
    *
    * {{{
    * val findUnresolved = scheme.cata(Passes.findUnresolvedAlg)
    * val issues: Vector[(String, String, String)] = findUnresolved(myDecl)
    * }}}
    *
    * @return a vector of `(declarationName, memberName, unresolvedFqn)` triples
    * @see [[validateResolved]] for a complete pass that fails on unresolved types
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
    * A validation pass that checks that no [[TypeExpr.Unresolved]] types remain in the module.
    *
    * If unresolved types are found, the pass fails via `Outcome.Left` with error diagnostics
    * listing each unresolved type and its location. If all types are resolved, the module
    * passes through unchanged.
    *
    * @tparam F the base effect type (must have an `Applicative` instance)
    * @return a [[Pass]] that validates type resolution completeness
    * @see [[findUnresolvedAlg]] for the underlying algebra
    */
  def validateResolved[F[_]: Applicative]: Pass[[A] =>> Outcome[F, A]] =
    Pass[[A] =>> Outcome[F, A]]("validate-resolved") { module =>
      val findUnresolved = scheme.cata(findUnresolvedAlg)
      val errors = module.units.flatMap { unit =>
        unit.declarations.flatMap(findUnresolved(_))
      }
      val diags = errors.map { (declName, memberName, fqn) =>
        Diagnostic(Severity.Error, s"Unresolved type '$fqn' in $declName.$memberName")
      }
      NonEmptyChain.fromSeq(diags) match
        case Some(nec) => Outcome.failNec(nec)
        case None      => Outcome.ok(module)
    }

  /**
    * Collects unresolved type FQNs from all parameters and return type of a function.
    *
    * @param declName the name of the enclosing declaration (for error reporting)
    * @param f        the function to inspect
    * @return a vector of `(declName, memberName, unresolvedFqn)` triples
    */
  private def findInFunc(declName: String, f: Func): Vector[(String, String, String)] =
    findInType(f.returnType).map(fqn => (declName, f.name, fqn)) ++
      f.params.flatMap(p => findInType(p.paramType).map(fqn => (declName, s"${f.name}.${p.name}", fqn)))

  /**
    * Collects all unresolved FQNs from a [[TypeExpr]] tree via `foldMap`.
    *
    * @param t the type expression to inspect
    * @return a vector of unresolved fully qualified names
    */
  private def findInType(t: TypeExpr): Vector[String] =
    t.foldMap {
      case TypeExpr.Unresolved(fqn) => Vector(fqn)
      case _                        => Vector.empty
    }
