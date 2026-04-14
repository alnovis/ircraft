package io.alnovis.ircraft.java

import cats.Id
import cats.data.{ Ior, IorT, NonEmptyChain }
import io.alnovis.ircraft.core.{ Diagnostic, Severity }

import java.util.{ Collections, List => JList, Optional }
import scala.jdk.CollectionConverters._

/**
  * A three-state result type for Java consumers, replacing cats `IorT`.
  *
  * Every operation in the ircraft Java API returns a `Result[T]`. It has exactly
  * three possible states:
  *
  *  - '''ok''' -- clean success, no diagnostics.
  *  - '''withWarnings''' -- success with one or more warnings attached.
  *  - '''error''' -- failure with one or more error diagnostics.
  *
  * ===Usage from Java===
  * {{{
  * Result<IrModule> result = ProtoLoweringFacade.lower(protoFile);
  * if (result.isSuccess()) {
  *     IrModule module = result.value();
  *     result.warnings().forEach(w -> System.out.println(w.message()));
  * } else {
  *     result.errors().forEach(e -> System.err.println(e.message()));
  * }
  * }}}
  *
  * @tparam T the type of the successful value
  * @see [[Result$.ok Result.ok]] to create a success
  * @see [[Result$.error(message:String)* Result.error]] to create an error
  */
final class Result[T] private (private val ior: Ior[Vector[Diagnostic], T]) {

  /** Returns `true` if this is a clean success with no diagnostics. */
  def isOk: Boolean = ior.isRight

  /** Returns `true` if this is a success with attached warnings. */
  def hasWarnings: Boolean = ior.isBoth

  /** Returns `true` if this is a failure. */
  def isError: Boolean = ior.isLeft

  /** Returns `true` if this contains a value (ok or withWarnings). */
  def isSuccess: Boolean = !isError

  /**
    * Returns the successful value.
    *
    * @throws NoSuchElementException if this is an error result
    */
  def value: T = ior match {
    case Ior.Right(a)   => a
    case Ior.Both(_, a) => a
    case Ior.Left(diags) =>
      val msgs = diags.map(_.message).mkString("; ")
      throw new NoSuchElementException(s"Result.value called on error: $msgs")
  }

  /**
    * Returns the successful value wrapped in `Optional`, or `Optional.empty()` if error.
    *
    * @return the value if present
    */
  def valueOpt: Optional[T] = ior match {
    case Ior.Right(a)   => Optional.of(a)
    case Ior.Both(_, a) => Optional.of(a)
    case Ior.Left(_)    => Optional.empty()
  }

  /**
    * Returns all diagnostics (warnings and errors). Empty list for ok results.
    *
    * @return unmodifiable list of diagnostics
    */
  def diagnostics: JList[Diagnostic] = ior match {
    case Ior.Right(_)    => Collections.emptyList()
    case Ior.Both(ds, _) => Collections.unmodifiableList(ds.asJava)
    case Ior.Left(ds)    => Collections.unmodifiableList(ds.asJava)
  }

  /**
    * Returns only error-severity diagnostics.
    *
    * @return unmodifiable list of errors
    */
  def errors: JList[Diagnostic] = {
    val all = ior match {
      case Ior.Left(ds)    => ds
      case Ior.Both(ds, _) => ds
      case Ior.Right(_)    => Vector.empty
    }
    Collections.unmodifiableList(all.filter(_.isError).asJava)
  }

  /**
    * Returns only warning-severity diagnostics.
    *
    * @return unmodifiable list of warnings
    */
  def warnings: JList[Diagnostic] = {
    val all = ior match {
      case Ior.Left(ds)    => ds
      case Ior.Both(ds, _) => ds
      case Ior.Right(_)    => Vector.empty
    }
    Collections.unmodifiableList(all.filter(_.isWarning).asJava)
  }

  /**
    * Transforms the successful value. Errors pass through unchanged.
    *
    * @param f function to apply to the value
    * @tparam U result type
    * @return transformed result
    */
  def map[U](f: java.util.function.Function[T, U]): Result[U] =
    new Result(ior.map(a => f.apply(a)))

  /**
    * Chains a dependent operation. If this is an error, the function is not called.
    * Warnings from both steps are accumulated.
    *
    * @param f function returning a new Result
    * @tparam U result type
    * @return chained result
    */
  def flatMap[U](f: java.util.function.Function[T, Result[U]]): Result[U] =
    new Result(ior.flatMap(a => f.apply(a).ior))

  /** Converts to cats `Ior` for Scala interop. */
  def toIor: Ior[Vector[Diagnostic], T] = ior

  /** Converts to cats `IorT[Id, NonEmptyChain[Diagnostic], T]` for Scala interop. */
  def toOutcome: IorT[Id, NonEmptyChain[Diagnostic], T] = {
    val mapped: Ior[NonEmptyChain[Diagnostic], T] = ior.leftMap { ds =>
      NonEmptyChain
        .fromSeq(ds)
        .getOrElse(
          NonEmptyChain.one(Diagnostic(Severity.Error, "unknown error"))
        )
    }
    IorT[Id, NonEmptyChain[Diagnostic], T](mapped)
  }

  override def toString: String = ior match {
    case Ior.Right(a)    => s"Result.ok($a)"
    case Ior.Both(ds, a) => s"Result.withWarnings($a, ${ds.size} diagnostics)"
    case Ior.Left(ds)    => s"Result.error(${ds.size} diagnostics)"
  }

  override def equals(obj: Any): Boolean = obj match {
    case other: Result[?] => ior == other.ior
    case _                => false
  }

  override def hashCode(): Int = ior.hashCode()
}

/**
  * Factory methods for [[Result]].
  *
  * ===Creating results===
  * {{{
  * Result<Integer> success  = Result.ok(42);
  * Result<Integer> warned   = Result.withWarnings(42, List.of(diagnostic));
  * Result<Integer> failed   = Result.error("something went wrong");
  * }}}
  */
object Result {

  /**
    * Creates a successful result with no diagnostics.
    *
    * @param value the successful value
    * @tparam T value type
    * @return ok result
    */
  def ok[T](value: T): Result[T] =
    new Result(Ior.Right(value))

  /**
    * Creates a successful result with attached warnings.
    * If the warnings list is empty, returns an ok result instead.
    *
    * @param value    the successful value
    * @param warnings list of warning diagnostics
    * @tparam T value type
    * @return result with warnings, or ok if warnings is empty
    */
  def withWarnings[T](value: T, warnings: JList[Diagnostic]): Result[T] =
    if (warnings.isEmpty) ok(value)
    else new Result(Ior.Both(warnings.asScala.toVector, value))

  /**
    * Creates an error result from a list of diagnostics.
    *
    * @param errors list of error diagnostics
    * @tparam T value type (phantom -- no value is present)
    * @return error result
    */
  def error[T](errors: JList[Diagnostic]): Result[T] =
    new Result(Ior.Left(errors.asScala.toVector))

  /**
    * Creates an error result from a single error message.
    *
    * @param message error description
    * @tparam T value type (phantom -- no value is present)
    * @return error result
    */
  def error[T](message: String): Result[T] =
    new Result(Ior.Left(Vector(Diagnostic(Severity.Error, message))))

  /**
    * Converts a cats `Ior` to a Result. For Scala interop.
    *
    * @param ior the Ior value
    * @tparam T value type
    * @return equivalent Result
    */
  def fromIor[T](ior: Ior[Vector[Diagnostic], T]): Result[T] =
    new Result(ior)

  /**
    * Converts a cats `IorT[Id, NonEmptyChain[Diagnostic], T]` to a Result. For Scala interop.
    *
    * @param outcome the IorT value
    * @tparam T value type
    * @return equivalent Result
    */
  def fromOutcome[T](outcome: IorT[Id, NonEmptyChain[Diagnostic], T]): Result[T] = {
    val ior: Ior[NonEmptyChain[Diagnostic], T] = outcome.value
    new Result(ior.leftMap(_.toChain.toVector))
  }
}
