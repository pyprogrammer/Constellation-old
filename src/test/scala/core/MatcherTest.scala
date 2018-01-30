package core

import graphutils.GraphMatcher
import org.scalatest._

import scalax.collection.mutable.Graph

class MatcherTest extends FunSuite {
  test("Graph Matcher should recognize a graph and itself") {
    val nodes = 0 until 4
    val edges = Seq(Argument(0, 1, ArgumentData(0, 0)))
    val graph = Graph.from[Int, Argument](nodes, edges)

    val matcher = new GraphMatcher(graph, graph, graph.order, (_, _) => true)
    val matches = matcher.findMatches()
    assert(matches.forall(_.size == graph.order))
  }
}
