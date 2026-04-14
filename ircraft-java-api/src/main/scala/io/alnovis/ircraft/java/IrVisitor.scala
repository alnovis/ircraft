package io.alnovis.ircraft.java

import java.util.{ List => JList }
import scala.jdk.CollectionConverters._

/**
  * Abstract base class implementing the Visitor pattern over the ircraft IR tree.
  *
  * <p>This class replaces the Scala {@code scheme.cata} (catamorphism) for Java consumers,
  * providing a familiar object-oriented visitor that dispatches on the {@link DeclKind} of
  * each {@link IrNode} and automatically recurses into nested declarations.</p>
  *
  * <p>To use, extend this class and implement all five {@code visitXxx} methods. Then
  * call {@link #visit} on any node to start traversal. For {@link TypeDeclView} nodes
  * that contain nested declarations, the visitor recurses into the nested nodes first and
  * passes the collected results to {@link #visitTypeDecl}.</p>
  *
  * <h3>Usage from Java</h3>
  * {{{
  * IrVisitor<String> nameCollector = new IrVisitor<String>() {
  *     public String visitTypeDecl(TypeDeclView decl, List<String> nestedResults) {
  *         return decl.name() + " [" + String.join(", ", nestedResults) + "]";
  *     }
  *     public String visitEnumDecl(EnumDeclView decl) {
  *         return "enum:" + decl.name();
  *     }
  *     public String visitFuncDecl(FuncDeclView decl) {
  *         return "func:" + decl.func().name();
  *     }
  *     public String visitAliasDecl(AliasDeclView decl) {
  *         return "alias:" + decl.name();
  *     }
  *     public String visitConstDecl(ConstDeclView decl) {
  *         return "const:" + decl.name();
  *     }
  * };
  *
  * IrNode node = ...;
  * String result = nameCollector.visit(node);
  * }}}
  *
  * @tparam T the result type produced by visiting each node
  * @see [[IrNode]]     for the nodes being visited
  * @see [[DeclKind]]   for the declaration kind discriminator
  */
abstract class IrVisitor[T] {

  /**
    * Visits a type declaration node.
    *
    * <p>Called after all nested declarations have been visited. The results of
    * visiting nested nodes are provided in {@code nestedResults} in the same order
    * as {@link TypeDeclView#nested}.</p>
    *
    * @param decl          the type declaration view
    * @param nestedResults results from visiting nested declarations (may be empty)
    * @return the result of visiting this type declaration
    */
  def visitTypeDecl(decl: TypeDeclView, nestedResults: JList[T]): T

  /**
    * Visits an enum declaration node.
    *
    * @param decl the enum declaration view
    * @return the result of visiting this enum declaration
    */
  def visitEnumDecl(decl: EnumDeclView): T

  /**
    * Visits a function declaration node.
    *
    * @param decl the function declaration view
    * @return the result of visiting this function declaration
    */
  def visitFuncDecl(decl: FuncDeclView): T

  /**
    * Visits a type alias declaration node.
    *
    * @param decl the alias declaration view
    * @return the result of visiting this alias declaration
    */
  def visitAliasDecl(decl: AliasDeclView): T

  /**
    * Visits a constant declaration node.
    *
    * @param decl the constant declaration view
    * @return the result of visiting this constant declaration
    */
  def visitConstDecl(decl: ConstDeclView): T

  /**
    * Visits an IR node, automatically dispatching to the appropriate {@code visitXxx}
    * method and recursing into nested declarations.
    *
    * <p>For {@link DeclKind#TypeDecl} nodes, nested declarations are visited first
    * (depth-first), and their results are passed to {@link #visitTypeDecl}. All other
    * node kinds are leaf nodes with no recursion.</p>
    *
    * @param node the IR node to visit
    * @return the result of visiting the node
    * @throws java.util.NoSuchElementException if the node's typed accessor returns empty
    *         (should not happen for well-formed nodes)
    */
  final def visit(node: IrNode): T = node.kind match {
    case DeclKind.TypeDecl =>
      val view          = node.asTypeDecl.get()
      val nestedResults = view.nested.asScala.map(visit).asJava
      visitTypeDecl(view, nestedResults)
    case DeclKind.EnumDecl  => visitEnumDecl(node.asEnumDecl.get())
    case DeclKind.FuncDecl  => visitFuncDecl(node.asFuncDecl.get())
    case DeclKind.AliasDecl => visitAliasDecl(node.asAliasDecl.get())
    case DeclKind.ConstDecl => visitConstDecl(node.asConstDecl.get())
  }
}
