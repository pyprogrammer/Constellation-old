package constellation.core

import scala.collection.mutable
import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.{TypeTag, typeTag}

trait Pattern[ParameterType] {
  val name: String = ""
  val implementations: mutable.Set[Implementation[ParameterType]] = new mutable.HashSet[Implementation[ParameterType]]

  val defaultTTs = Seq.empty[TypeTag[_]]

  override def toString: String = s"$name[$tt]($parameter)"

  val parameter: ParameterType

  val tt: Seq[TypeTag[_]]

  val inputTypes: Seq[TypeTag[_]]
  val outputTypes: Seq[TypeTag[_]]

  def unapply(arg: Pattern[ParameterType]): Option[ParameterType] = {
    Some(parameter)
  }
}

trait Nop extends Pattern[Unit] {
  override val name: String = "Nop"
  override val tt = defaultTTs
  override val parameter: Unit = ()
  override val inputTypes: Seq[universe.TypeTag[_]] = Seq(universe.typeTag[Any])
  override val outputTypes: Seq[universe.TypeTag[_]] = Seq(universe.typeTag[Any])
}


