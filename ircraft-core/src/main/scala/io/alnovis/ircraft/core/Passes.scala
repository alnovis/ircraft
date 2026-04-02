package io.alnovis.ircraft.core

import cats.*
import cats.data.Chain
import cats.syntax.all.*
import io.alnovis.ircraft.core.ir.*

/** Built-in validation passes. */
object Passes:

  /** Checks that no Unresolved types remain in the module. Emits Error diagnostics. */
  def validateResolved[F[_]: Monad]: Pass[F] = Pass[F]("validate-resolved") { module =>
    val errors = module.units.flatMap { unit =>
      unit.declarations.flatMap(findUnresolved)
    }
    errors.traverse_ { (declName, memberName, fqn) =>
      Pipe.error[F](s"Unresolved type '$fqn' in $declName.$memberName")
    }.as(module)
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

  private def findInType(t: TypeExpr): Vector[String] = t match
    case TypeExpr.Unresolved(fqn) => Vector(fqn)
    case TypeExpr.ListOf(e)       => findInType(e)
    case TypeExpr.MapOf(k, v)     => findInType(k) ++ findInType(v)
    case TypeExpr.Optional(i)     => findInType(i)
    case TypeExpr.SetOf(e)        => findInType(e)
    case TypeExpr.TupleOf(es)     => es.flatMap(findInType)
    case TypeExpr.Applied(b, as)  => findInType(b) ++ as.flatMap(findInType)
    case TypeExpr.FuncType(ps, r) => ps.flatMap(findInType) ++ findInType(r)
    case TypeExpr.Union(as)       => as.flatMap(findInType)
    case TypeExpr.Intersection(cs) => cs.flatMap(findInType)
    case _                        => Vector.empty
