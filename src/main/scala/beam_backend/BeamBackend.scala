package beam_backend

import core.{ArgumentData, Backend, Pattern, ProgramGraph}
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.Pipeline.PipelineVisitor
import org.apache.beam.sdk.runners.TransformHierarchy
import org.apache.beam.sdk.values.{PCollection}
import scala.collection.mutable
import scalax.collection.edge.LkDiEdge

object BeamBackend extends Backend[Pipeline]{
  val patternMap = Map(
    "cross" -> Pattern.getInstance[Unit]("Cross"),
    "zip" -> Pattern.getInstance[Unit]("Zip"),
    "map" -> Pattern.getInstance[_ => _]("Map"),
    "fold" -> Pattern.getInstance[(_, _) => _]("Fold"),
    "RootNode" -> Pattern.getInstance[Unit]("Root"),
    "parallelize" -> Pattern.getInstance[Unit]("Create"),
    "saveAsTextFile" -> Pattern.getInstance[Unit]("Output")
  )
  override val patterns: Iterable[Pattern[_]] = patternMap.values
  override val name = "Beam"

  class Traverser extends PipelineVisitor.Defaults {
    val programGraph: ProgramGraph[Pattern[_]] = new ProgramGraph[Pattern[_]]
    override def leaveCompositeTransform(node: TransformHierarchy#Node): Unit = {
      println(f"Leaving composite transform: $node\n")
    }

    val outputMapping = mutable.Map[PCollection[_], mutable.ArrayBuffer[(Int, Int)]]()

    var currentNodeId = 0

    def getOp(fullname: String): Option[String] = {
      println(f"Get Op Fullname: $fullname")
      if (fullname == "") {
        return Some("RootNode")
      }
      val opExtractor = raw"(.*?)@".r
      val matched = opExtractor.findFirstMatchIn(fullname)
      if (matched.isEmpty) {
        return None
      }
      Some(matched.get.group(1))
    }

    def getNewId(): Int = {
      val v = currentNodeId
      currentNodeId += 1
      v
    }


    private def addNodeToGraph(node: TransformHierarchy#Node): Unit = {

      val op = getOp(node.getFullName)
      if (op.nonEmpty) {
      } else {
        throw new IllegalArgumentException(s"Could not find op name $node")
      }
      val nodeId = getNewId()
      var outputCnt = 0
      node.getOutputs.values().forEach {
        _ match {
          case key: PCollection[_] => {
            if (outputMapping contains key) {
              println(f"Duplicate: $key")
              outputMapping(key).append((nodeId, outputCnt))
            } else {
              outputMapping(key) = mutable.ArrayBuffer((nodeId, outputCnt))
            }
            outputCnt += 1
          }
        }
      }

      programGraph.innerGraph.add(nodeId)
      programGraph.idMap(nodeId) = patternMap(op.get)
      programGraph.labelMap(nodeId) = node.getFullName

      var inputCnt = 0
      node.getInputs.values().forEach {
        _ match {
          case tag: PCollection[_] => {
            if (!(outputMapping contains tag)) {
              println(f"Missing tag: $tag")
            }
            //          val (sourceNode, sourceId) = outputMapping(tag)
            outputMapping(tag).foreach {
              d => {
                val (sourceNode, sourceId) = d
                val edge = LkDiEdge(sourceNode, nodeId)(ArgumentData(sourceId, inputCnt))
                programGraph.innerGraph.add(edge)
              }
            }
            inputCnt += 1
          }
        }
      }
    }

    override def enterCompositeTransform(node: TransformHierarchy#Node): PipelineVisitor.CompositeBehavior = {
      println("Composite Transform")
      println(f"Node: $node")
      println(node.getInputs)
      println(node.getOutputs)
      println()
      addNodeToGraph(node)
      if (node.isRootNode) {
        return PipelineVisitor.CompositeBehavior.ENTER_TRANSFORM
      }
      PipelineVisitor.CompositeBehavior.DO_NOT_ENTER_TRANSFORM
    }

    override def leavePipeline(pipeline: Pipeline): Unit = {
      println("Finishing")
    }

    override def visitPrimitiveTransform(node: TransformHierarchy#Node): Unit = {
      println("PrimitiveTransform")
      println(f"Node: $node")
      println(node.getInputs)
      println(node.getOutputs)
      println()
      addNodeToGraph(node)
    }
  }

  override def extract(domainGraph: Pipeline) = {
    val traverser = new Traverser
    domainGraph.traverseTopologically(traverser)
    traverser.programGraph
  }

  override def implement(programGraph: ProgramGraph[Pattern[_]]) = ???
}
