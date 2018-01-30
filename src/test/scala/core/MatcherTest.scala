package core

import graphutils.GraphMatcher
import org.scalatest._

import scalax.collection.edge.LkDiEdge
import scalax.collection.mutable.Graph

class MatcherTest extends FunSuite {
  test("Graph Matcher should recognize a graph and itself") {
    val nodes = 0 until 4
    val edges = Seq(
      (0, 1, 0, 0),
      (0, 2, 1, 0),
      (1, 3, 0, 0),
      (2, 3, 0, 1),
      (3, 4, 0, 0)
    ).map {
      d => {LkDiEdge(d._1, d._2)(ArgumentData(d._3, d._4))}
    }
    val graph = Graph.from[Int, LkDiEdge](nodes, edges)


    val matcher = new GraphMatcher(graph, graph, graph.order, (_, _) => true)
    val matches = matcher.findMatches()
    assert(matches.forall(_.size == graph.order))
    assert(matches.size == 1)
  }
}
