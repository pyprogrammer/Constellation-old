package core

import scala.collection.mutable
import scalax.collection.GraphEdge.{EdgeCopy, ExtendedKey, LoopFreeEdge, NodeProduct}
import scalax.collection.GraphPredef.OuterEdge
import scalax.collection.edge.LkBase.LkEdgeCompanion
import scalax.collection.edge.LkDiEdge
import scalax.collection.mutable.Graph

class ProgramGraph[T] {
  private val innerGraph = Graph[Int, LkDiEdge]()
  private var idMap = mutable.Map[Int, T]()
  private var nextInt = 0

  def +(t: T): Int = {
    idMap.put(nextInt, t)
    innerGraph.add(nextInt)
    nextInt += 1
    nextInt - 1
  }
}

case class ArgumentData(in: Int, out: Int)

