package core

trait Backend[T] {
  val patterns: Iterable[Pattern[_]]
  val name: String

  def extract(domainGraph: T): ProgramGraph[Pattern[_]]
  def implement(programGraph: ProgramGraph[Pattern[_]]): T
}
