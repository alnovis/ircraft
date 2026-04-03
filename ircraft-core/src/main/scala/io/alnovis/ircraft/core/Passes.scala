package io.alnovis.ircraft.core

import cats.*
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.*

/** Built-in validation passes. */
object Passes:

  /** Checks that no Unresolved types remain in the module. Raises DiagnosticError on failure. */
  def validateResolved[F[_]: MonadThrow]: Pass[F] = Pass[F]("validate-resolved") { module =>
    val errors = module.units.flatMap { unit =>
      unit.declarations.flatMap(findUnresolved)
    }
    if errors.nonEmpty then
      MonadThrow[F].raiseError(DiagnosticError(
        errors.map { (declName, memberName, fqn) =>
          Diagnostic(Severity.Error, s"Unresolved type '$fqn' in $declName.$memberName")
        }
      ))
    else
      module.pure[F]
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
