package core

import scala.collection.mutable
import scala.reflect.ClassTag

class Pattern[ParameterType] protected (val name: String) {
  val implementations: mutable.Set[Implementation[ParameterType]] = new mutable.HashSet[Implementation[ParameterType]]
  type PType = ParameterType
}

object Pattern {
  private val patterns = mutable.Map[String, Pattern[_]]()
  private val validators = mutable.Map[String, mutable.HashSet[_ => Boolean]]()

  def getInstance[T: ClassTag](name: String): Pattern[T] = {
    if (patterns contains name) {
      val pattern = patterns(name)
      pattern.asInstanceOf[Pattern[T]]
      return pattern.asInstanceOf[Pattern[T]]
    }
    val pattern = new Pattern[T](name)
    patterns(name) = pattern
    pattern
  }

  def registerValidator[T](name: String, validator: T => Boolean): Unit = {
    if (!(validators contains name)) {
      validators(name) = mutable.HashSet[_ => Boolean]()
    }

    validators(name) += validator
  }

  def getValidators(name: String): Seq[_ => Boolean] = {
    if (validators contains name) {
      return validators(name).toSeq
    }
    Seq.empty
  }
}

class ParameterizedPattern[ParameterType](val pattern: Pattern[ParameterType], val parameter: ParameterType) {
  private val validators = Pattern.getValidators(pattern.name)
  private val passes = validators.forall {
    _.asInstanceOf[ParameterType => Boolean](parameter)
  }
  if (!passes) throw new IllegalArgumentException(f"$parameter does not satisfy validators for $pattern.")
}
