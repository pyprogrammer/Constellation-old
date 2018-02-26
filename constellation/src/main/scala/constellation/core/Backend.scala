package constellation.core

import com.typesafe.scalalogging.LazyLogging

trait Backend[T] extends LazyLogging {
  val patterns: Iterable[Pattern[_]]
  val name: String

  def extract(domainGraph: T): ProgramGraph[ParameterizedPattern[_]]
  def implement(programGraph: ProgramGraph[ParameterizedPattern[_]]): T
}
