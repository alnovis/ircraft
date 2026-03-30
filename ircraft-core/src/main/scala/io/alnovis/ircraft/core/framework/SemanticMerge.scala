package io.alnovis.ircraft.core.framework

import io.alnovis.ircraft.core.*
import io.alnovis.ircraft.core.semantic.ops.*

/**
  * Generic merge of two Semantic IR modules.
  *
  * Unions types (InterfaceOp, ClassOp, EnumClassOp) by name:
  *   - Same name -> merge methods/fields (union by method name)
  *   - Different names -> include both
  *   - Type conflict (same method, different return type) -> diagnostic warning
  *
  * Adds `merge.sources` attribute on the merged module's FileOps
  * and `merge.presentIn` attribute on methods/fields that exist in only some inputs.
  *
  * This is a generic PoC. Domain-specific merge (proto version conflicts,
  * field renumbering, etc.) should be implemented as a custom ModuleTransform
  * in the consumer plugin.
  */
object SemanticMerge:

  private val SourcesKey   = "merge.sources"
  private val PresentInKey = "merge.presentIn"

  /**
    * Create a binary merge transform.
    *
    * @param sourceName1 label for the first module (e.g., "v1")
    * @param sourceName2 label for the second module (e.g., "v2")
    */
  def merge(sourceName1: String, sourceName2: String): ModuleTransform =
    new ModuleTransform:
      val name: String        = "semantic-merge"
      override val description: String = s"Merge Semantic IR: $sourceName1 + $sourceName2"

      def transform(inputs: Vector[IrModule], context: PassContext): PassResult =
        require(inputs.size == 2, s"SemanticMerge expects 2 inputs, got ${inputs.size}")
        val m1 = inputs(0)
        val m2 = inputs(1)
        val diagnostics = List.newBuilder[DiagnosticMessage]

        val files1 = collectFiles(m1)
        val files2 = collectFiles(m2)
        val nonFiles1 = m1.topLevel.filterNot(_.isInstanceOf[FileOp])
        val nonFiles2 = m2.topLevel.filterNot(_.isInstanceOf[FileOp])

        val allPackages = (files1.keys ++ files2.keys).toVector.distinct
        val mergedFiles = allPackages.flatMap: pkg =>
          val f1 = files1.getOrElse(pkg, Vector.empty)
          val f2 = files2.getOrElse(pkg, Vector.empty)
          mergeFileGroups(pkg, f1, f2, sourceName1, sourceName2, diagnostics)

        val mergedTopLevel = mergedFiles ++ nonFiles1 ++ nonFiles2
        val mergedModule = IrModule(
          s"${m1.name}+${m2.name}",
          mergedTopLevel,
          m1.attributes,
          m1.span
        )
        PassResult(mergedModule, diagnostics.result())

  private def collectFiles(module: IrModule): Map[String, Vector[FileOp]] =
    module.topLevel.collect { case f: FileOp => f }.groupBy(_.packageName)

  private def mergeFileGroups(
    pkg: String,
    files1: Vector[FileOp],
    files2: Vector[FileOp],
    src1: String,
    src2: String,
    diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): Vector[FileOp] =
    val types1 = files1.flatMap(_.types)
    val types2 = files2.flatMap(_.types)
    val merged = mergeTypes(types1, types2, src1, src2, diagnostics)
    if merged.isEmpty then Vector.empty
    else
      val sourcesAttr = AttributeMap(Attribute.StringListAttr(SourcesKey, List(src1, src2)))
      Vector(FileOp(pkg, merged, sourcesAttr))

  private def mergeTypes(
    types1: Vector[Operation],
    types2: Vector[Operation],
    src1: String,
    src2: String,
    diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): Vector[Operation] =
    val allNames = (types1.map(typeName) ++ types2.map(typeName)).distinct

    allNames.flatMap: name =>
      val t1 = types1.find(t => typeName(t) == name)
      val t2 = types2.find(t => typeName(t) == name)
      (t1, t2) match
        case (Some(a: InterfaceOp), Some(b: InterfaceOp)) =>
          Some(mergeInterfaces(a, b, src1, src2, diagnostics))
        case (Some(a: EnumClassOp), Some(b: EnumClassOp)) =>
          Some(mergeEnums(a, b))
        case (Some(a: ClassOp), Some(b: ClassOp)) =>
          Some(mergeClasses(a, b, src1, src2, diagnostics))
        case (Some(a), Some(b)) =>
          diagnostics += DiagnosticMessage.warning(
            s"Type '$name' has different kinds in sources: ${a.kind} vs ${b.kind}",
            a.span
          )
          Some(a)
        case (Some(a), None) => Some(a)
        case (None, Some(b)) => Some(b)
        case (None, None)    => None

  private def mergeInterfaces(
    a: InterfaceOp,
    b: InterfaceOp,
    src1: String,
    src2: String,
    diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): InterfaceOp =
    val merged = mergeMethods(a.methods, b.methods, src1, src2, a.name, diagnostics)
    val mergedNested = mergeTypes(a.nestedTypes, b.nestedTypes, src1, src2, diagnostics)
    InterfaceOp(
      name = a.name,
      modifiers = a.modifiers,
      typeParams = a.typeParams,
      extendsTypes = (a.extendsTypes ++ b.extendsTypes).distinct,
      methods = merged,
      nestedTypes = mergedNested,
      javadoc = a.javadoc.orElse(b.javadoc),
      attributes = a.attributes
    )

  private def mergeClasses(
    a: ClassOp,
    b: ClassOp,
    src1: String,
    src2: String,
    diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): ClassOp =
    val mergedMethods = mergeMethods(a.methods, b.methods, src1, src2, a.name, diagnostics)
    val mergedFields = mergeFields(a.fields, b.fields, a.name, diagnostics)
    val mergedNested = mergeTypes(a.nestedTypes, b.nestedTypes, src1, src2, diagnostics)
    ClassOp(
      name = a.name,
      modifiers = a.modifiers,
      typeParams = a.typeParams,
      superClass = a.superClass.orElse(b.superClass),
      implementsTypes = (a.implementsTypes ++ b.implementsTypes).distinct,
      fields = mergedFields,
      constructors = a.constructors,
      methods = mergedMethods,
      nestedTypes = mergedNested,
      javadoc = a.javadoc.orElse(b.javadoc),
      attributes = a.attributes
    )

  private def mergeEnums(a: EnumClassOp, b: EnumClassOp): EnumClassOp =
    val allConstants = (a.constants ++ b.constants).distinctBy(_.name)
    EnumClassOp(
      name = a.name,
      modifiers = a.modifiers,
      constants = allConstants,
      fields = a.fields,
      constructors = a.constructors,
      methods = a.methods,
      javadoc = a.javadoc.orElse(b.javadoc),
      attributes = a.attributes
    )

  private def mergeMethods(
    methods1: Vector[MethodOp],
    methods2: Vector[MethodOp],
    src1: String,
    src2: String,
    typeName: String,
    diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): Vector[MethodOp] =
    val allNames = (methods1.map(_.name) ++ methods2.map(_.name)).distinct
    allNames.map: methodName =>
      val m1 = methods1.find(_.name == methodName)
      val m2 = methods2.find(_.name == methodName)
      (m1, m2) match
        case (Some(a), Some(b)) =>
          if a.returnType != b.returnType then
            diagnostics += DiagnosticMessage.warning(
              s"Type conflict in $typeName.$methodName: ${a.returnType} vs ${b.returnType}",
              a.span
            )
          presentInBoth(a, src1, src2)
        case (Some(a), None) => presentIn(a, src1)
        case (None, Some(b)) => presentIn(b, src2)
        case _               => throw IllegalStateException("unreachable")

  private def mergeFields(
    fields1: Vector[FieldDeclOp],
    fields2: Vector[FieldDeclOp],
    typeName: String,
    diagnostics: scala.collection.mutable.Builder[DiagnosticMessage, List[DiagnosticMessage]]
  ): Vector[FieldDeclOp] =
    val allNames = (fields1.map(_.name) ++ fields2.map(_.name)).distinct
    allNames.map: fieldName =>
      val f1 = fields1.find(_.name == fieldName)
      val f2 = fields2.find(_.name == fieldName)
      (f1, f2) match
        case (Some(a), Some(b)) =>
          if a.fieldType != b.fieldType then
            diagnostics += DiagnosticMessage.warning(
              s"Field type conflict in $typeName.$fieldName: ${a.fieldType} vs ${b.fieldType}",
              a.span
            )
          a
        case (Some(a), None) => a
        case (None, Some(b)) => b
        case _               => throw IllegalStateException("unreachable")

  // -- Attribute helpers ------------------------------------------------------

  private def presentIn(method: MethodOp, source: String): MethodOp =
    method.copy(attributes =
      method.attributes + Attribute.StringListAttr(PresentInKey, List(source))
    )

  private def presentInBoth(method: MethodOp, src1: String, src2: String): MethodOp =
    method.copy(attributes =
      method.attributes + Attribute.StringListAttr(PresentInKey, List(src1, src2))
    )

  private def typeName(op: Operation): String = op match
    case i: InterfaceOp => i.name
    case c: ClassOp     => c.name
    case e: EnumClassOp => e.name
    case other          => other.qualifiedName
