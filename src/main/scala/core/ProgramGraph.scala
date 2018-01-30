package core

import scala.collection.mutable
import scalax.collection.GraphEdge.{EdgeCopy, ExtendedKey, LoopFreeEdge, NodeProduct}
import scalax.collection.GraphPredef.OuterEdge
import scalax.collection.edge.LkDiEdge
import scalax.collection.mutable.Graph

class ProgramGraph[T] {
  private val innerGraph = Graph[Int, Argument]()
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

case class Argument[+N](output: N, input: N, data: ArgumentData)
  extends LkDiEdge[N](NodeProduct(output, input))
    with    ExtendedKey[N]
    with    EdgeCopy[Argument]
    with    OuterEdge[N,Argument]
    with    LoopFreeEdge[N]
{
  private def this(nodes: Product, data: ArgumentData) {
    this(nodes.productElement(0).asInstanceOf[N],
      nodes.productElement(1).asInstanceOf[N], data)
  }
  def keyAttributes = Seq(data)
  override def copy[NN](newNodes: Product) = new Argument[NN](newNodes, data)
  override protected def attributesToString = s" ($data)"
}

