package graphutils

import scalax.collection.Graph
import scalax.collection.edge.LkDiEdge

case class MatchState(fullToFrag: Map[Int, Int], fragToFull: Map[Int, Int], size: Int) {

  def +(fullNode: Int, fragNode: Int): MatchState = {
    MatchState(fullToFrag + (fullNode -> fragNode), fragToFull + (fragNode -> fullNode), size + 1)
  }

  def +(mapping: (Int, Int)): MatchState = {
    this + (mapping._1, mapping._2)
  }
}

// A variant of the VF2 Algorithm, with node type matching.
class GraphMatcher[T](val full: Graph[Int, LkDiEdge],
                      val fragment: Graph[Int, LkDiEdge],
                      val fragmentSize: Int,
                      // TypeChecks between full node id and fragment node id
                      val typeChecker: (Int, Int) => Boolean
                  ) {
  def findMatches(state: MatchState = MatchState(Map.empty[Int, Int],
    Map.empty[Int, Int], 0)): Iterable[MatchState] = {
    if (state.size == fragmentSize) {
      return List(state)
    }
    candidatePairs(state).filter {
      pair => {
        isFeasible(state, pair._1, pair._2)
      }
    }.flatMap {
      pair => {
        findMatches(state + (pair._1 -> pair._2))
      }
    }
  }

  private def candidatePairs(state: MatchState): Iterable[(Int, Int)] = {
    val tOut1 = outTerminalSet(state.fullToFrag.keys, full)
    val tOut2 = outTerminalSet(state.fragToFull.keys, fragment)
    if (!(tOut1.isEmpty || tOut2.isEmpty)) {
      val min = tOut2.min
      return tOut1.map {
        (_, min)
      }
    }

    val tIn1 = inTerminalSet(state.fullToFrag.keys, full)
    val tIn2 = inTerminalSet(state.fragToFull.keys, fragment)

    if ((tOut1.isEmpty && tOut2.isEmpty) && !(tIn1.isEmpty || tIn2.isEmpty)) {
      val min = tIn2.min
      return tIn1.map {
        (_, min)
      }
    }

    val emptiness = Seq(tIn1, tIn2, tOut1, tOut2)

    if (emptiness.forall {
      _.isEmpty
    }) {
      val unmatchedFull = full.toOuterNodes.toSet -- state.fullToFrag.keys
      val unmatchedFrag = fragment.toOuterNodes.toSet -- state.fragToFull.keys
      val min = unmatchedFrag.min
      return unmatchedFull.map {
        (_, min)
      }
    }

    List.empty[(Int, Int)]
  }

  private def edgeSetMatches(fullEdges: Set[full.EdgeT], fragEdges: Set[fragment.EdgeT]): Boolean = {
    fullEdges.map {_.label} == fragEdges.map {_.label}
  }

  private def feasibilityRPred(state: MatchState,
                               fullInternalNode: full.NodeT,
                               fragInternalNode: fragment.NodeT): Boolean = {
    fullInternalNode.diPredecessors.filter {state.fullToFrag contains _.value}.foreach {
      fullPred => {
        // Return false if the predecessor isn't mapped in the fragment graph
        if (!(state.fragToFull contains state.fullToFrag(fullPred.value))) {
          return false
        }

        val fragPred = fragment get state.fullToFrag(fullPred.value)
        val fullEdges = fullInternalNode.incomingFrom(fullPred)
        val fragEdges = fragInternalNode.incomingFrom(fragPred)

        if (fullEdges != fragEdges) { return false }
      }
    }

    fragInternalNode.diPredecessors.filter {state.fragToFull contains _.value}.foreach({
      fragPred => {
        if (!(state.fullToFrag contains state.fragToFull(fragPred.value))) {
          return false
        }

        val fullPred = full get state.fragToFull(fragPred.value)
        val fragEdges = fragInternalNode.incomingFrom(fragPred)
        val fullEdges = fullInternalNode.incomingFrom(fullPred)
        if (fullEdges != fragEdges) { return false }
      }
    })
    true
  }

  private def feasibilityRSucc(state: MatchState,
                               fullInternalNode: full.NodeT,
                               fragInternalNode: fragment.NodeT): Boolean = {
    fullInternalNode.diSuccessors.filter { state.fullToFrag contains _.value }.foreach {
      fullSucc => {
        if (!(state.fragToFull contains state.fullToFrag(fullSucc.value))) {
          return false
        }

        val fragSucc = fragment get state.fullToFrag(fullSucc.value)
        val fullEdges = fullInternalNode.outgoingTo(fullSucc)
        val fragEdges = fragInternalNode.outgoingTo(fragSucc)

        if (fullEdges != fragEdges) { return false }
      }
    }

    fragInternalNode.diSuccessors.filter {state.fragToFull contains _.value}.foreach({
      fragSucc => {
        if (!(state.fullToFrag contains state.fragToFull(fragSucc.value))) {
          return false
        }

        val fullSucc = full get state.fragToFull(fragSucc.value)
        val fragEdges = fragInternalNode.outgoingTo(fragSucc)
        val fullEdges = fullInternalNode.outgoingTo(fullSucc)

        if (fullEdges != fragEdges) { return false }
      }
    })

    true
  }

  private def feasibilityRIn(state: MatchState,
                                 fullInternalNode: full.NodeT,
                                 fragInternalNode: fragment.NodeT): Boolean = {
    val fullSet = inTerminalSet(state.fullToFrag.keySet, full)
    val fragSet = inTerminalSet(state.fragToFull.keySet, fragment)
    val fullSucc = fullInternalNode.diSuccessors.map {_.value}.count(fullSet contains)
    val fragSucc = fragInternalNode.diSuccessors.map {_.value}.count(fullSet contains)
    val fullPred = fullInternalNode.diPredecessors.map {_.value}.count(fragSet contains)
    val fragPred = fragInternalNode.diPredecessors.map {_.value}.count(fragSet contains)

    fullSucc >= fragSucc && fullPred >= fragPred
  }

  private def feasibilityROut(state: MatchState,
                              fullInternalNode: full.NodeT,
                              fragInternalNode: fragment.NodeT): Boolean = {
    val fullSet = outTerminalSet(state.fullToFrag.keySet, full)
    val fragSet = outTerminalSet(state.fragToFull.keySet, fragment)
    val fullSucc = fullInternalNode.diSuccessors.map {_.value}.count(fullSet contains)
    val fragSucc = fragInternalNode.diSuccessors.map {_.value}.count(fullSet contains)
    val fullPred = fullInternalNode.diPredecessors.map {_.value}.count(fragSet contains)
    val fragPred = fragInternalNode.diPredecessors.map {_.value}.count(fragSet contains)

    fullSucc >= fragSucc && fullPred >= fragPred
  }


  private def feasibilityRNew(state: MatchState,
                              fullInternalNode: full.NodeT,
                              fragInternalNode: fragment.NodeT): Boolean = {
    val tSet1 = terminalSet(state.fullToFrag.keys, full)
    val nt1 = full.toOuterNodes.filterNot {
      p => (state.fullToFrag.keySet contains p) || (tSet1 contains p)}

    val tSet2 = terminalSet(state.fragToFull.keys, fragment)
    val nt2 = fragment.toOuterNodes.filterNot {
      p => (state.fragToFull.keySet contains p) || (tSet2 contains p)}

    val predSet1 = fullInternalNode.diPredecessors.map {_.value}
    val predSet2 = fragInternalNode.diPredecessors.map {_.value}
    val succSet1 = fullInternalNode.diSuccessors.map {_.value}
    val succSet2 = fragInternalNode.diSuccessors.map {_.value}

    (nt1.count {predSet1 contains} >= nt2.count {predSet1 contains}) &&
      (nt1.count {succSet1 contains} >= nt2.count {succSet2 contains})
  }


  private def isFeasible(state: MatchState, fullNode: Int, fragmentNode: Int): Boolean = {

    if (!typeChecker(fullNode, fragmentNode)) {
      return false
    }

    val fullInternalNode = full get fullNode
    val fragInternalNode = fragment get fragmentNode
    val RPred = feasibilityRPred(state, fullInternalNode, fragInternalNode)

    val criteria = Seq[(MatchState, full.NodeT, fragment.NodeT) => Boolean](
      feasibilityRPred,
      feasibilityRSucc,
      feasibilityRIn,
      feasibilityROut,
      feasibilityRNew
    )

    criteria.forall(_(state, fullInternalNode, fragInternalNode))
  }

  private def outTerminalSet(nodes: Iterable[Int], graph: Graph[Int, LkDiEdge]): Set[Int] = {
    nodes.map {
      key => (graph get key).diSuccessors.map {
        _.value
      }
    }.fold(Set.empty[Int]) {
      _ ++ _
    } -- nodes
  }

  private def inTerminalSet(nodes: Iterable[Int], graph: Graph[Int, LkDiEdge]): Set[Int] = {
    nodes.map {
      key => (graph get key).diPredecessors.map {
        _.value
      }
    }.fold(Set.empty[Int]) {
      _ ++ _
    } -- nodes
  }

  private def terminalSet (nodes: Iterable[Int], graph: Graph[Int, LkDiEdge]): Set[Int] = {
    outTerminalSet(nodes, graph) union inTerminalSet(nodes, graph)
  }
}
