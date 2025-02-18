package com.nrinaudo.csv

import simulacrum.{noop, typeclass}

/** Decodes CSV cells into usable types.
  *
  * By itself, an instance of `CellDecoder` is not terribly interesting. It becomes useful when combined with
  * `RowDecoder`, which relies on any implicit `CellDecoder` it has in scope to parse entire rows.
  *
  * If, for example, you need to parse CSV data that contains ISO 8601 formatted dates, you can't immediately call
  * [[CsvInput.rows]] with a type argument of `List[Date]`: dates are not supported natively (because they can be
  * serialised in so many different ways).
  *
  * This can be remedied simply by writing the following:
  * {{{
  *   implicit val dateDecoder = CellDecoder(s => DecodeResult(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").parse(s)))
  * }}}
  *
  * See the [[CellDecoder$ companion object]] for default implementations and construction methods.
  */
@typeclass trait CellDecoder[A] {
  /** Turns the content of a CSV cell into an `A`. */
  @noop def decode(s: String): DecodeResult[A]

  /** Turns the content of the specified cell into an `A`.
    *
    * The purpose of this method is to protect against index out of bound exceptions. Should the specified index not
    * exist, a [[DecodeResult.DecodeFailure]] instance will be returned.
    */
  @noop def decode(ss: Seq[String], index: Int): DecodeResult[A] =
    if(ss.isDefinedAt(index)) decode(ss(index))
    else                      DecodeResult.DecodeFailure

  /** Turns an instance of `CsvInput[A]` into one of `CsvInput[B]`.
    *
    * This allows developers to adapt existing instances of `CellDecoder` rather than write one from scratch.
    */
  @noop def map[B](f: A => B): CellDecoder[B] = CellDecoder(s => decode(s).map(f))

  @noop def flatMap[B](f: A => CellDecoder[B]): CellDecoder[B] = CellDecoder(s => decode(s).flatMap(a => f(a).decode(s)))
}

/** Defines convenience methods for creating and retrieving instances of `CellDecoder`.
  *
  * Implicit default implementations of standard types are also declared here, always bringing them in scope with a low
  * priority.
  *
  * These default implementations can also be useful when writing more complex instances: if you need to write a
  * `CellDecoder[B]` and have both a `CellDecoder[A]` and a `A => B`, you need just use [[CellDecoder.map]] to create
  * your implementation.
  */
object CellDecoder {
  /** Creates a new instance of [[CellDecoder]] that uses the specified function to parse data. */
  def apply[A](f: String => DecodeResult[A]): CellDecoder[A] = new CellDecoder[A] {
    override def decode(a: String) = f(a)
  }

  /** Turns a cell into a `String` value. */
  implicit val string: CellDecoder[String]     = CellDecoder(s => DecodeResult.success(s))
  /** Turns a cell into a `Char` value. */
  implicit val char:   CellDecoder[Char]       = CellDecoder(s => if(s.length == 1) DecodeResult.success(s(0)) else DecodeResult.decodeFailure)
  /** Turns a cell into an `Int` value. */
  implicit val int   : CellDecoder[Int]        = CellDecoder(s => DecodeResult(s.toInt))
  /** Turns a cell into a `Float` value. */
  implicit val float : CellDecoder[Float]      = CellDecoder(s => DecodeResult(s.toFloat))
  /** Turns a cell into a `Double` value. */
  implicit val double: CellDecoder[Double]     = CellDecoder(s => DecodeResult(s.toDouble))
  /** Turns a cell into a `Long` value. */
  implicit val long  : CellDecoder[Long]       = CellDecoder(s => DecodeResult(s.toLong))
  /** Turns a cell into a `Short` value. */
  implicit val short : CellDecoder[Short]      = CellDecoder(s => DecodeResult(s.toShort))
  /** Turns a cell into a `Byte` value. */
  implicit val byte  : CellDecoder[Byte]       = CellDecoder(s => DecodeResult(s.toByte))
  /** Turns a cell into a `Boolean` value. */
  implicit val bool  : CellDecoder[Boolean]    = CellDecoder(s => DecodeResult(s.toBoolean))
  /** Turns a cell into a `BigInt` value. */
  implicit val bigInt: CellDecoder[BigInt]     = CellDecoder(s => DecodeResult(BigInt.apply(s)))
  /** Turns a cell into a `BigDecimal` value. */
  implicit val bigDec: CellDecoder[BigDecimal] = CellDecoder(s => DecodeResult(BigDecimal.apply(s)))

  /** Turns a cell into an instance of `Option[A]`, provided `A` has an implicit `CellDecoder` in scope.
    *
    * Any non-empty string will map to `Some`, the empty string to `None`.
    */
  implicit def opt[A: CellDecoder]: CellDecoder[Option[A]] = CellDecoder { s =>
    if(s.isEmpty) DecodeResult.success(None)
    else          CellDecoder[A].decode(s).map(Option.apply)
  }

  /** Turns a cell into an instance of `Either[A, B]`, provided `A` and `B` have an inplicit `CellDecoder` in scope.
    *
    * This is done by first attempting to parse the cell as an `A`. If that fails, we'll try parsing it as a `B`. If that
    * fails as well, [[DecodeResult.DecodeFailure]] will be returned.
    */
  implicit def either[A: CellDecoder, B: CellDecoder]: CellDecoder[Either[A, B]] =
    CellDecoder { s => CellDecoder[A].decode(s).map(a => Left(a): Either[A, B])
      .orElse(CellDecoder[B].decode(s).map(b => Right(b): Either[A, B]))
    }
}