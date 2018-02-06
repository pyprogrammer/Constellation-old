package core

import scala.collection.mutable

class Pattern[ParameterType] protected (name: String) {
  val implementations: mutable.Set[Implementation[ParameterType]] = new mutable.HashSet[Implementation[ParameterType]]
  type PType = ParameterType
}

object Pattern {
  private val patterns = mutable.Map[String, Pattern[_]]()

  def getInstance[T](name: String): Pattern[T] = {
    if (patterns contains name) {
      val pattern = patterns(name)
      if (!pattern.isInstanceOf[Pattern[T]]) {
        throw new IllegalArgumentException("")
      }
      return pattern.asInstanceOf[Pattern[T]]
    }
    val pattern = new Pattern[T](name)
    patterns(name) = pattern
    pattern
  }
}

class ParameterizedPattern[ParameterType](val pattern: Pattern[ParameterType], val parameter: ParameterType)
