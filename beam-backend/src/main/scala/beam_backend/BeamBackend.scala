package beam_backend

import constellation.core._
import constellation.core.macros.AnnotatedFunction
import org.apache.beam.sdk.Pipeline
import org.apache.beam.sdk.Pipeline.PipelineVisitor
import org.apache.beam.sdk.io.TextIO
import org.apache.beam.sdk.io.fs.ResourceId
import org.apache.beam.sdk.options.ValueProvider
import org.apache.beam.sdk.runners.TransformHierarchy
import org.apache.beam.sdk.transforms.ParDo.{MultiOutput, SingleOutput}
import org.apache.beam.sdk.transforms.{Combine, Create}
import org.apache.beam.sdk.values.PCollection
import org.apache.commons.lang.NotImplementedException
import constellation.utils.typeUtils

import scala.collection.convert.Wrappers.IterableWrapper
import scala.collection.mutable
import scala.reflect.runtime.universe._
import scala.reflect.{ClassTag, classTag}
import scala.collection.JavaConverters._
import scalax.collection.edge.LkDiEdge
import patterns.core
import patterns.core.{Aggregate, Output}

import scala.reflect.runtime.universe


object BeamBackend extends Backend[Pipeline] {
  override val name = "Beam"

  override val patterns: Iterable[ClassTag[_]] = Seq(
    classTag[core.Zip], classTag[core.Map], classTag[core.Aggregate], classTag[core.Root], classTag[core.Output])

  class Traverser extends PipelineVisitor.Defaults {
    val programGraph: ProgramGraph[Pattern[_]] = new ProgramGraph[Pattern[_]]
    override def leaveCompositeTransform(node: TransformHierarchy#Node): Unit = {
      logger.debug(s"Leaving composite transform: $node\n")
    }

    private val outputMapping = mutable.Map[PCollection[_], mutable.ArrayBuffer[(Int, Int)]]()

    var currentNodeId = 0

    def getOp(fullname: String): Option[String] = {
      // This is a really stupid way of doing this
      // But scio uses a stacktrace to get the name in the first place.
      logger.debug(f"Get Op Fullname: $fullname")
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

    def getNewId: Int = {
      val v = currentNodeId
      currentNodeId += 1
      v
    }


    private def addNodeToGraph(node: TransformHierarchy#Node): Unit = {
      val nodeId: Int = getNewId
      var outputCnt = 0
      node.getOutputs.values().forEach {
        case key: PCollection[_] => {
          if (outputMapping contains key) {
            logger.debug(f"Duplicate: $key")
            outputMapping(key).append((nodeId, outputCnt))
          } else {
            outputMapping(key) = mutable.ArrayBuffer((nodeId, outputCnt))
          }
          outputCnt += 1
        }
      }

      programGraph.innerGraph.add(nodeId)
      val Pattern = createPattern(node)
      programGraph.idMap(nodeId) = Pattern
      programGraph.labelMap(nodeId) = node.getFullName

      var inputCnt = 0
      node.getInputs.values().forEach {
        case tag: PCollection[_] => {
          if (!(outputMapping contains tag)) {
            logger.debug(f"Missing tag: $tag")
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
      logger.debug("Composite Transform")
      logger.debug(f"Node: $node")
      logger.debug(s"${node.getInputs}")
      logger.debug(s"${node.getOutputs}")
      addNodeToGraph(node)
      if (node.isRootNode) {
        return PipelineVisitor.CompositeBehavior.ENTER_TRANSFORM
      }
      PipelineVisitor.CompositeBehavior.DO_NOT_ENTER_TRANSFORM
    }

    override def leavePipeline(pipeline: Pipeline): Unit = {
      logger.debug("Finishing")
    }

    override def visitPrimitiveTransform(node: TransformHierarchy#Node): Unit = {
      logger.debug("PrimitiveTransform")
      logger.debug(f"Node: $node")
      logger.debug(s"${node.getInputs}")
      logger.debug(s"${node.getOutputs}")
      addNodeToGraph(node)
    }

    private def createPattern(node: TransformHierarchy#Node): Pattern[_] = {
      val name = getOp(node.getFullName).get
      node.getTransform match {
        case so: SingleOutput[_, _] =>
          logger.debug("SingleOutput")
          val func = typeUtils.getAttribute[_ => _](so.getFn, Seq("g"))
          if (!func.isInstanceOf[AnnotatedFunction]) {
            logger.debug(s"Function not annotated: ${node.getFullName}")
          } else {
            logger.debug(s"Function is annotated: ${node.getFullName}")
          }
          name match {
            case "map" => {
              val typeArgs = func.asInstanceOf[AnnotatedFunction].typeCheckedTree.tpe.typeArgs

              return new core.Map {
                override val tt = defaultTTs
                override val parameter: AnnotatedFunction = func.asInstanceOf[AnnotatedFunction]
                override val inputTypes: Seq[universe.TypeTag[_]] = Seq(typeUtils.createNestedTypeTag(typeOf[Seq[_]], typeArgs.head))
                override val outputTypes: Seq[universe.TypeTag[_]] = Seq(typeUtils.createNestedTypeTag(typeOf[Seq[_]], typeArgs.last))
              }
            }
            case "saveAsTextFile" => {
              return new Nop {}
            }
          }
        case mo: MultiOutput[_, _] =>
          logger.error("MultiOutput is not implemented")
        case null =>
          logger.debug("No transform")
          return new core.Root {
            override val tt = defaultTTs
            override val parameter: Unit = ()
            override val inputTypes: Seq[universe.TypeTag[_]] = defaultTTs
            override val outputTypes: Seq[universe.TypeTag[_]] = defaultTTs
          }
        case cv: Create.Values[_] =>
          logger.debug("Create")
          cv.getElements match {
            case el: IterableWrapper[_] => {
              val t: Type = typeUtils.classToType(el.underlying.head.getClass)
              return new core.Create {
                override val tt: Seq[universe.TypeTag[_]] = Seq(typeUtils.typeToTag(t))
                override val parameter: (Seq[Any], universe.Type) = (cv.getElements.asScala.toSeq, t)
                override val inputTypes: Seq[universe.TypeTag[_]] = defaultTTs
                override val outputTypes: Seq[universe.TypeTag[_]] = Seq(typeUtils.typeToTag(t))
              }
            }
          }
//
        case cg: Combine.Globally[_, _] =>
          logger.debug("Combine")
          name match {
            case "fold" => {
              val tg = typeUtils.getAttribute[AnnotatedFunction](cg.getFn, Seq("s")).tp.typeArgs
              return new Aggregate {
                override val tt: Seq[universe.TypeTag[_]] = defaultTTs
                override val parameter = (
                  typeUtils.getAttribute[AnnotatedFunction](cg.getFn, Seq("s")),
                  typeUtils.getAttribute[AnnotatedFunction](cg.getFn, Seq("c")),
                  typeUtils.getAttribute[Any](cg.getFn, Seq("zeroValue$1")))
                override val inputTypes: Seq[universe.TypeTag[_]] = Seq(typeUtils.createNestedTypeTag(typeOf[Seq[_]], tg.head))
                override val outputTypes: Seq[universe.TypeTag[_]] = Seq(typeUtils.typeToTag(tg.last))
              }
            }
          }
        case tio: TextIO.Write =>
          logger.debug("Output")
          val prefix = typeUtils.getAttribute[ValueProvider[ResourceId]](tio, Seq("inner", "filenamePrefix"))
          val suffix = typeUtils.getAttribute[String](tio, Seq("inner", "filenameSuffix"))
          return new Output {override val tt = Seq()
            override val parameter: (String, String) = (prefix.get.toString, suffix)
            override val outputTypes: Seq[universe.TypeTag[_]] = defaultTTs
            override val inputTypes: Seq[universe.TypeTag[_]] = Seq(typeTag[Any])
          }

        case _ => throw new IllegalArgumentException(f"Could not match $node")
      }
      throw new NotImplementedException("Not Implemented")
    }
  }

  override def extract(domainGraph: Pipeline): ProgramGraph[Pattern[_]] = {
    val traverser = new Traverser
    domainGraph.traverseTopologically(traverser)
    logger.debug(traverser.programGraph.dumpDot())
    Backend.eliminateNop(traverser.programGraph)
    traverser.programGraph
  }

  override def implement(programGraph: ProgramGraph[Pattern[_]]): Pipeline = {
    ???
    // TODO
  }

  override def execute(data: Pipeline): Unit = ???
}
