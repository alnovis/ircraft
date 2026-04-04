package io.alnovis.ircraft.core

import cats._
import cats.data._
import io.alnovis.ircraft.core.ir._
import io.alnovis.ircraft.core.ir.TypeExpr.TypeExprOps

object Passes {

  private type Diags = NonEmptyChain[Diagnostic]

  def validateResolved[F[_]: Applicative]: Pass[({type L[A] = IorT[F, Diags, A]})#L] =
    Pass[({type L[A] = IorT[F, Diags, A]})#L]("validate-resolved") { module =>
      val errors = module.units.flatMap { unit =>
        unit.declarations.flatMap(findUnresolved)
      }
      val diags = errors.map { case (declName, memberName, fqn) =>
        Diagnostic(Severity.Error, s"Unresolved type '$fqn' in $declName.$memberName")
      }
      NonEmptyChain.fromSeq(diags) match {
        case Some(nec) => Outcome.failNec(nec)
        case None      => Outcome.ok(module)
      }
    }

  private def findUnresolved(decl: Decl): Vector[(String, String, String)] =
    decl match {
      case td: Decl.TypeDecl =>
        td.fields.flatMap(f => findInType(f.fieldType).map(fqn => (td.name, f.name, fqn))) ++
          td.functions.flatMap(f => findInFunc(td.name, f)) ++
          td.nested.flatMap(findUnresolved)
      case ed: Decl.EnumDecl =>
        ed.functions.flatMap(f => findInFunc(ed.name, f))
      case _ => Vector.empty
    }

  private def findInFunc(declName: String, f: io.alnovis.ircraft.core.ir.Func): Vector[(String, String, String)] =
    findInType(f.returnType).map(fqn => (declName, f.name, fqn)) ++
      f.params.flatMap(p => findInType(p.paramType).map(fqn => (declName, s"${f.name}.${p.name}", fqn)))

  private def findInType(t: TypeExpr): Vector[String] =
    t.foldMap {
      case TypeExpr.Unresolved(fqn) => Vector(fqn)
      case _ => Vector.empty
    }
}
