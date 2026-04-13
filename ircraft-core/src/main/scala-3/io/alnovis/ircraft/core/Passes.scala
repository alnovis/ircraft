package io.alnovis.ircraft.core

import cats._
import cats.data.NonEmptyChain
import io.alnovis.ircraft.core.algebra.Fix
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.SemanticF._

/** Built-in validation passes. */
object Passes:

  /** Checks that no Unresolved types remain in the module. Fails via Outcome.Left on errors. */
  def validateResolved[F[_]: Applicative]: Pass[[A] =>> Outcome[F, A]] =
    Pass[[A] =>> Outcome[F, A]]("validate-resolved") { module =>
      val errors = module.units.flatMap { unit =>
        unit.declarations.flatMap(findUnresolved)
      }
      val diags = errors.map { (declName, memberName, fqn) =>
        Diagnostic(Severity.Error, s"Unresolved type '$fqn' in $declName.$memberName")
      }
      NonEmptyChain.fromSeq(diags) match
        case Some(nec) => Outcome.failNec(nec)
        case None      => Outcome.ok(module)
    }

  private def findUnresolved(fix: Fix[SemanticF]): Vector[(String, String, String)] =
    fix.unfix match
      case TypeDeclF(name, _, fields, functions, nested, _, _, _, _, _) =>
        fields.flatMap(f => findInType(f.fieldType).map(fqn => (name, f.name, fqn))) ++
          functions.flatMap(f => findInFunc(name, f)) ++
          nested.flatMap(findUnresolved)
      case EnumDeclF(name, _, functions, _, _, _, _) =>
        functions.flatMap(f => findInFunc(name, f))
      case _ => Vector.empty

  private def findInFunc(declName: String, f: Func): Vector[(String, String, String)] =
    findInType(f.returnType).map(fqn => (declName, f.name, fqn)) ++
      f.params.flatMap(p => findInType(p.paramType).map(fqn => (declName, s"${f.name}.${p.name}", fqn)))

  private def findInType(t: TypeExpr): Vector[String] =
    t.foldMap {
      case TypeExpr.Unresolved(fqn) => Vector(fqn)
      case _                        => Vector.empty
    }
