package scalaz.stream.csv

import java.io.StringWriter

import com.nrinaudo.csv.laws.discipline.arbitrary._
import com.nrinaudo.csv.scalaz.stream.ops._
import org.scalatest.FunSuite
import org.scalatest.prop.GeneratorDrivenPropertyChecks

import scalaz.concurrent.Task
import scalaz.stream.Process._
import scalaz.stream.{Cause, Process}

class SerializationSpec extends FunSuite with GeneratorDrivenPropertyChecks {
  def read(raw: String): List[List[String]] =
    raw.asUnsafeCsvSource[List[String]](',', false).runLog.run.toList

  def write(data: List[List[String]]): String = {
    val sw = new StringWriter()
    val iterator = data.iterator

    Process.repeatEval(Task.delay { if(iterator.hasNext) iterator.next() else throw Cause.Terminated(Cause.End) })
      .to(sw.asCsvSink(',', Seq.empty)).run.run

    sw.toString
  }

  test("Serialized CSV data should be parsed correctly") {
    forAll(csv) { ss: List[List[String]] => assert(read(write(ss)) == ss) }
  }
}
