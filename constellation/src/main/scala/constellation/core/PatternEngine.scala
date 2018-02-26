package constellation.core

import scalax.collection.mutable.Graph
import scalax.collection.GraphPredef._, scalax.collection.GraphEdge._

class PatternEngine {
  // specialized -> generalized
  private val generalizationGraph = Graph[Pattern[_], DiEdge]()

  def isSpecialization(child: Pattern[_], parent: Pattern[_]) : Boolean = {
    val path = (generalizationGraph get child) pathTo (generalizationGraph get parent)
    path.nonEmpty
  }

  def registerSpecialization(child: Pattern[_], parent: Pattern[_]) : Unit = {
    generalizationGraph add (child ~> parent)
  }
}
