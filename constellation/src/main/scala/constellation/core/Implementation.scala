package constellation.core

import scalax.collection.Graph
import scalax.collection.edge.LkDiEdge

class Implementation[ParameterType] (
                                      val name: String,
                                      val pattern: Pattern[ParameterType],
                                      val skeleton: ProgramGraph[Pattern[_]],

                                    // Given an isomorphism and the graph, find the parameters
                                     val lifter: (ProgramGraph[ParameterizedPattern[_]], Map[Int, Int]) => ParameterType
                                    ) {

  pattern.implementations.add(this)

  def lift(programGraph: ProgramGraph[ParameterizedPattern[_]], iso: Map[Int, Int]):
  ParameterizedImplementation[ParameterType] = {
    new ParameterizedImplementation[ParameterType](this, lifter(programGraph, iso))
  }
}

class ParameterizedImplementation[ParameterType] (val implementation: Implementation[ParameterType],
                                                  val parameters: ParameterType)
