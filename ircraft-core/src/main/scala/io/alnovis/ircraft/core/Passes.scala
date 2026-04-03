package io.alnovis.ircraft.core

import cats.*
import cats.data.*
import io.alnovis.ircraft.core.ir.*

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

  private def findUnresolved(decl: Decl): Vector[(String, String, String)] =
    decl match
      case td: Decl.TypeDecl =>
        td.fields.flatMap(f => findInType(f.fieldType).map(fqn => (td.name, f.name, fqn))) ++
          td.functions.flatMap(f => findInFunc(td.name, f)) ++
          td.nested.flatMap(findUnresolved)
      case ed: Decl.EnumDecl =>
        ed.functions.flatMap(f => findInFunc(ed.name, f))
      case _ => Vector.empty

  private def findInFunc(declName: String, f: io.alnovis.ircraft.core.ir.Func): Vector[(String, String, String)] =
    findInType(f.returnType).map(fqn => (declName, f.name, fqn)) ++
      f.params.flatMap(p => findInType(p.paramType).map(fqn => (declName, s"${f.name}.${p.name}", fqn)))

  private def findInType(t: TypeExpr): Vector[String] =
    t.foldMap {
      case TypeExpr.Unresolved(fqn) => Vector(fqn)
      case _                        => Vector.empty
    }
