package beam_backend

import core._
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.Pipeline.PipelineVisitor
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.io.fs.ResourceId
import org.apache.beam.sdk.options.ValueProvider
import org.apache.beam.sdk.runners.TransformHierarchy
import org.apache.beam.sdk.transforms.{Combine, Create, ParDo}
import org.apache.beam.sdk.transforms.ParDo.{MultiOutput, SingleOutput}
import org.apache.beam.sdk.values.PCollection
import org.apache.commons.lang.NotImplementedException
import utils.typeUtils

import scala.collection.convert.Wrappers.IterableWrapper
import scala.collection.mutable
import scala.reflect.ClassTag
import scalax.collection.edge.LkDiEdge
import scala.reflect.runtime.universe._


object BeamBackend extends Backend[Pipeline]{
  val patternMap: Map[String, Pattern[_]] = Map(
    "cross" -> Pattern.getInstance[Unit]("Cross"),
    "zip" -> Pattern.getInstance[Unit]("Zip"),
    "map" -> Pattern.getInstance[_ => _]("Map"),
    "aggregate" -> Pattern.getInstance[((_, _) => _, (_, _) => _, _)]("Aggregate"),
    "fold" -> Pattern.getInstance[((_, _) => _, (_, _) => _, _)]("Aggregate"),
    "RootNode" -> Pattern.getInstance[Unit]("Root"),
    "create" -> Pattern.getInstance[(Any, Type)]("Create"),
    "output" -> Pattern.getInstance[(String, String)]("Output"),
  )
  override val patterns: Iterable[Pattern[_]] = patternMap.values
  override val name = "Beam"

  class Traverser extends PipelineVisitor.Defaults {
    val programGraph: ProgramGraph[ParameterizedPattern[_]] = new ProgramGraph[ParameterizedPattern[_]]
    override def leaveCompositeTransform(node: TransformHierarchy#Node): Unit = {
      println(f"Leaving composite transform: $node\n")
    }

    private val outputMapping = mutable.Map[PCollection[_], mutable.ArrayBuffer[(Int, Int)]]()

    var currentNodeId = 0

    def getOp(fullname: String): Option[String] = {
      // This is a really stupid way of doing this
      // But scio uses a stacktrace to get the name in the first place.
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
      val nodeId = getNewId()
      var outputCnt = 0
      node.getOutputs.values().forEach {
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

      programGraph.innerGraph.add(nodeId)
      val parameterizedPattern = createParameterizedPattern(node)
      programGraph.idMap(nodeId) = parameterizedPattern
      programGraph.labelMap(nodeId) = node.getFullName

      var inputCnt = 0
      node.getInputs.values().forEach {
        case tag: PCollection[_] => {
          if (!(outputMapping contains tag)) {
            println(f"Missing tag: $tag")
          }
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

    private def createParameterizedPattern(node: TransformHierarchy#Node): ParameterizedPattern[_] = {
      val name = getOp(node.getFullName).get
      node.getTransform match {
        case so: SingleOutput[_, _] =>
          val cl = so.getFn.getClass
          val m = cl.getDeclaredField("g")
          m.setAccessible(true)
          println("SingleOutput")
          val func = m.get(so.getFn)
          name match {
            case "map" => {
              val pattern = patternMap("map").asInstanceOf[Pattern[_ => _]]
              return new ParameterizedPattern[_ => _](
                pattern, func.asInstanceOf[_ => _]
              )
            }
            case "saveAsTextFile" => {
              return new ParameterizedPattern[Unit](Pattern.getInstance[Unit]("Nop"), ())
            }
          }
        case mo: MultiOutput[_, _] =>
          println("MultiOutput")
        case null =>
          println("No transform")
          return new ParameterizedPattern[Unit](patternMap("RootNode").asInstanceOf[Pattern[Unit]], ())
        case cv: Create.Values[_] =>
          println("Create")
          cv.getElements match {
            case el: IterableWrapper[_] => {
              val t: Type = typeUtils.classToType(el.underlying.head.getClass)
              return new ParameterizedPattern[(Any, Type)](patternMap("create").asInstanceOf[Pattern[(Any, Type)]],
                (cv.getElements, t))
            }
          }

        case cg: Combine.Globally[_, _] =>
          println("Combine")
          val cl = cg.getFn.getClass
          val s = cl.getDeclaredField("s")
          val c = cl.getDeclaredField("c")
          val zv = cl.getDeclaredField("zeroValue$1")
          s.setAccessible(true)
          c.setAccessible(true)
          zv.setAccessible(true)
          name match {
            case "fold" => {
              type FuncType = (_, _) => _
              type PType = (FuncType, FuncType, _)
              return new ParameterizedPattern[PType](
                patternMap("fold").asInstanceOf[Pattern[PType]],
                (typeUtils.getAttribute[FuncType](cg.getFn, Seq("s")),
                  typeUtils.getAttribute[FuncType](cg.getFn, Seq("c")),
                  typeUtils.getAttribute[Any](cg.getFn, Seq("zeroValue$1")))
              )
            }
          }
        case tio: TextIO.Write =>
          println("Output")
          val prefix = typeUtils.getAttribute[ValueProvider[ResourceId]](tio, Seq("inner", "filenamePrefix"))
          val suffix = typeUtils.getAttribute[String](tio, Seq("inner", "filenameSuffix"))
          val path = prefix.get().getCurrentDirectory + prefix.get().getFilename + "." + suffix
          type PType = (String, String)
          val pattern = patternMap("output").asInstanceOf[Pattern[PType]]
          return new ParameterizedPattern[PType](
            pattern, (prefix.get().getCurrentDirectory + prefix.get().getFilename, suffix)
          )

        case _ => throw new IllegalArgumentException(f"Could not match $node")
      }
      throw new NotImplementedException("Not Implemented")
    }
  }

  override def extract(domainGraph: Pipeline): ProgramGraph[ParameterizedPattern[_]] = {
    val traverser = new Traverser
    domainGraph.traverseTopologically(traverser)
    println(traverser.programGraph.dumpDot())
    traverser.programGraph
  }

  override def implement(programGraph: ProgramGraph[ParameterizedPattern[_]]): Pipeline = {
    ???
    // TODO
  }
}
