package core

import scala.collection.mutable

class Pattern[ParameterType] (name: String) {
  val implementations: mutable.Set[Implementation[ParameterType]] = new mutable.HashSet[Implementation[ParameterType]]
}

class ParameterizedPattern[ParameterType](val pattern: Pattern[ParameterType], val parameter: ParameterType)
