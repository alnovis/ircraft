package io.alnovis.ircraft.core.semantic.passes

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.Traversal.*
import io.alnovis.ircraft.core.semantic.ops.*

/**
  * Resolves simple type names to fully-qualified names for correct import generation.
  *
  * After lowering (e.g., Proto -> Semantic), type references use simple names like `Money` or `GeoLocation`.
  * The emitter only generates imports for FQN with dots. This pass builds a type registry from the module
  * and replaces simple names with FQN so that the emitter can produce correct import statements.
  *
  * Generic: works for any source dialect (proto, OpenAPI, TOML) and any target language (Java, Kotlin, Scala).
  *
  * {{{
  * // Before: NamedType("Money") -- no import generated
  * // After:  NamedType("com.example.api.Money") -- import generated correctly
  *
  * // Nested types:
  * // Before: NamedType("GeoLocation") -- unresolvable
  * // After:  NamedType("com.example.api.Address.GeoLocation") -- imported as static inner
  * }}}
  */
object ImportResolutionPass extends Pass:

  val name: String        = "import-resolution"
  val description: String = "Resolves simple type names to fully-qualified names for correct import generation"

  def run(module: IrModule, context: PassContext): PassResult =
    val (registry, diagnostics) = buildRegistry(module)
    if registry.isEmpty then return PassResult(module, diagnostics)
    val transformed = transformModule(module, registry)
    // Debug: log registry size
    PassResult(transformed, diagnostics)

  // ── Phase 1: Build type registry ───────────────────────────────────────

  private sealed trait RegistryEntry
  private case class UniqueType(fqn: String)          extends RegistryEntry
  private case class AmbiguousType(fqns: Set[String]) extends RegistryEntry

  private def buildRegistry(module: IrModule): (Map[String, RegistryEntry], List[DiagnosticMessage]) =
    val registry    = scala.collection.mutable.Map.empty[String, RegistryEntry]
    val diagnostics = List.newBuilder[DiagnosticMessage]

    module.topLevel.foreach:
      case file: FileOp =>
        file.types.foreach(registerType(_, file.packageName, parentPath = Nil, registry, diagnostics))
      case _ => ()

    (registry.toMap, diagnostics.result())

  private def registerType(
      op: Operation,
      packageName: String,
      parentPath: List[String],
      registry: scala.collection.mutable.Map[String, RegistryEntry],
      diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): Unit =
    val typeName = op match
      case i: InterfaceOp => Some(i.name)
      case c: ClassOp     => Some(c.name)
      case e: EnumClassOp => Some(e.name)
      case _              => None

    typeName.foreach: name =>
      val fqn = (packageName :: parentPath ::: List(name)).mkString(".")

      registry.get(name) match
        case None =>
          registry(name) = UniqueType(fqn)
        case Some(UniqueType(existingFqn)) if existingFqn != fqn =>
          registry(name) = AmbiguousType(Set(existingFqn, fqn))
          diagnostics += DiagnosticMessage.warning(
            s"Type name '$name' is ambiguous: $existingFqn vs $fqn. References will not be auto-resolved."
          )
        case Some(AmbiguousType(fqns)) =>
          registry(name) = AmbiguousType(fqns + fqn)
        case _ => () // same FQN already registered

      // Recurse into nested types
      val nestedTypes = op match
        case i: InterfaceOp => i.nestedTypes
        case c: ClassOp     => c.nestedTypes
        case _              => Vector.empty

      val newPath = parentPath ::: List(name)
      nestedTypes.foreach(registerType(_, packageName, newPath, registry, diagnostics))

  // ── Phase 2: Transform TypeRefs ────────────────────────────────────────

  private def transformModule(module: IrModule, registry: Map[String, RegistryEntry]): IrModule =
    val resolve = resolveTypeRef(registry)
    module.transform:
      case cls: ClassOp =>
        cls.copy(
          superClass      = cls.superClass.map(resolve),
          implementsTypes = cls.implementsTypes.map(resolve)
        )
      case iface: InterfaceOp =>
        iface.copy(
          extendsTypes = iface.extendsTypes.map(resolve)
        )
      case m: MethodOp =>
        m.copy(
          returnType = resolve(m.returnType),
          parameters = m.parameters.map(p => p.copy(paramType = resolve(p.paramType)))
        )
      case f: FieldDeclOp =>
        f.copy(fieldType = resolve(f.fieldType))
      case ct: ConstructorOp =>
        ct.copy(
          parameters = ct.parameters.map(p => p.copy(paramType = resolve(p.paramType)))
        )
      case e: EnumClassOp =>
        e.copy(implementsTypes = e.implementsTypes.map(resolve))

  private def resolveTypeRef(registry: Map[String, RegistryEntry])(ref: TypeRef): TypeRef =
    val self = resolveTypeRef(registry)
    ref match
      case TypeRef.NamedType(name) if !name.contains(".") =>
        registry.get(name) match
          case Some(UniqueType(fqn)) => TypeRef.NamedType(fqn)
          case _                     => ref // ambiguous or unknown -- leave as-is
      case TypeRef.ParameterizedType(base, args) =>
        TypeRef.ParameterizedType(self(base), args.map(self))
      case TypeRef.ListType(elem) =>
        TypeRef.ListType(self(elem))
      case TypeRef.MapType(k, v) =>
        TypeRef.MapType(self(k), self(v))
      case TypeRef.OptionalType(inner) =>
        TypeRef.OptionalType(self(inner))
      case TypeRef.UnionType(alts) =>
        TypeRef.UnionType(alts.map(self))
      case TypeRef.WildcardType(bound) =>
        TypeRef.WildcardType(bound.map(self))
      case _ => ref
