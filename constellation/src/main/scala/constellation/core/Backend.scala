package constellation.core

import com.typesafe.scalalogging.LazyLogging

import scala.reflect.ClassTag
import scala.reflect.runtime.universe.{Type, TypeTag}
import scalax.collection.edge.LkDiEdge

abstract class Backend[T: TypeTag] extends LazyLogging {
  val patterns: Iterable[ClassTag[_]]

  val name: String

  def extract(domainGraph: T): ProgramGraph[Pattern[_]]
  def implement(programGraph: ProgramGraph[Pattern[_]]): T

  def execute[U](data: T): U
}

object Backend {
  def eliminateNop(programGraph: ProgramGraph[Pattern[_]]): ProgramGraph[Pattern[_]] = {

    val condition = (p: (Int, Pattern[_])) => {
      p._2 match {
        case _: Nop => true
        case _ => false
      }
    }

    val targets = (programGraph.idMap filter condition).keys

    targets foreach {
      id => {
        val node = programGraph.innerGraph.get(id)
        if (node.incoming.size != 1) throw new UnsupportedOperationException("Cannot remove node with more than 1 input")
        val inEdge = node.incoming.head
        val fromLabel = inEdge.label match {
          case ArgumentData(i, o) => o
        }
        node.outgoing foreach {
          e => {
            val newLabel = ArgumentData(
              e.label match { case ArgumentData(i, o) => i},
              fromLabel
            )

            programGraph.innerGraph.remove(e.toOuter)
            programGraph.innerGraph.add(LkDiEdge(inEdge.from.toOuter, e.to.toOuter)(newLabel))
          }
        }

        programGraph.idMap.remove(id)
        programGraph.labelMap.remove(id)
        programGraph.innerGraph.remove(id)
        programGraph.innerGraph.remove(inEdge.toOuter)
      }
    }

    programGraph
  }
}
