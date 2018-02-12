package core

trait Backend[T] {
  val patterns: Iterable[Pattern[_]]
  val name: String

  def extract(domainGraph: T): ProgramGraph[ParameterizedPattern[_]]
  def implement(programGraph: ProgramGraph[ParameterizedPattern[_]]): T
}
