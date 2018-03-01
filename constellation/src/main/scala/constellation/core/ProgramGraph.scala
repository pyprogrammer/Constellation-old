package constellation.core

import scala.collection.mutable
import scalax.collection.edge.LkDiEdge
import scalax.collection.mutable.{Graph => mutableGraph}
import scalax.collection.Graph
import scalax.collection.io.dot._

class ProgramGraph[T](val innerGraph: mutableGraph[Int, LkDiEdge] = mutableGraph[Int, LkDiEdge](),
                      val idMap: mutable.Map[Int, T] = mutable.Map[Int, T](),
                      val labelMap: mutable.Map[Int, String] = mutable.Map[Int, String]()) {


  private val root = DotRootGraph(directed = true, id = Some(Id("ProgramGraph")))

  def dumpDot(): String = {
    innerGraph.toDot(root, edgeTransformer, cNodeTransformer = Some(cNodeTransformer))
  }

  private def edgeTransformer(edge: Graph[Int, LkDiEdge]#EdgeT): Option[(DotGraph, DotEdgeStmt)] = {
    Some((
      root,
      DotEdgeStmt(
        NodeId(edge.from.toOuter),
        NodeId(edge.to.toOuter),
        Seq(DotAttr(Id("label"), Id(edge.label.toString)))
      )
    ))
  }

  private def cNodeTransformer(node: Graph[Int, LkDiEdge]#NodeT): Option[(DotGraph, DotNodeStmt)] = {
    Some((
      root,
      DotNodeStmt(
        NodeId(node.toOuter),
        Seq(DotAttr(Id("label"), Id(labelMap.getOrElse(node.toOuter, "Unlabeled"))))
      )
    ))
  }
}

case class ArgumentData(in: Int, out: Int)

