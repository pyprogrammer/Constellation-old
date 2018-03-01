package spatial_backend

import argon.core.{Exp, State, createLog}
import argon.lang.FixPt
import argon.lang.typeclasses.{FALSE, TRUE, _16, _32}
import constellation.core.{Backend, Pattern, ProgramGraph}
import patterns.core
import spatial.SpatialCompiler

import scala.collection.mutable
import scala.reflect.runtime.universe._
import scala.tools.reflect.ToolBox
import scala.reflect.{ClassTag, classTag}
import scala.language.reflectiveCalls

object SpatialBackend extends Backend[(State, argon.core.Block[_])]{
  override val patterns: Iterable[ClassTag[_]] = Seq(
    classTag[core.Zip], classTag[core.Map], classTag[core.Aggregate], classTag[core.Root], classTag[core.Output])

  override val name = "Spatial"

  private lazy val toolbox = runtimeMirror(getClass.getClassLoader).mkToolBox()

  private val typeMap = Map[Type, Type](
    typeOf[Double] -> typeOf[FixPt[TRUE, _32, _32]],
    typeOf[java.lang.Double] -> typeOf[FixPt[TRUE, _32, _32]],
    typeOf[Float] -> typeOf[FixPt[TRUE, _16, _16]])

  override def extract(domainGraph: (State, argon.core.Block[_])): ProgramGraph[Pattern[_]] = ???

  override def implement(programGraph: ProgramGraph[Pattern[_]]): (State, argon.core.Block[_]) = {

    val topo = programGraph.innerGraph.topologicalSort
    if (topo.isLeft) throw new IllegalArgumentException("Spatial Backend does not support cyclic graphs")

    var pre = mutable.ArrayBuffer[Tree]()
    var accel = mutable.ArrayBuffer[Tree]()
    var post = mutable.ArrayBuffer[Tree]()

    val sizeMap = mutable.Map[Int, Int]()

    val locationMap = mutable.Map[Int, mutable.ArrayBuffer[Tree]]()

    topo.right.get.toOuter.foreach {
      d => {
        val pattern = programGraph.idMap(d)
        println(pattern)
        val varName = TermName(s"x$d")
        pattern match {
          case r: core.Root => logger.debug("Starting at root")

          case c: core.Create =>
            val typeString = typeMap(c.parameter._2)
            val seqString = s"List[$typeString](${c.parameter._1.map({x => s"$typeString($x)"}).mkString(", ")})"
            accel += q"val $varName = RegFile[$typeString](${c.parameter._1.size}, ${toolbox.parse(seqString)})"
            sizeMap(d) = c.parameter._1.size

            locationMap(d) = accel

          case m: core.Map =>
            val incoming = programGraph.innerGraph.get(d).incoming
            if (incoming.size != 1) throw new IllegalArgumentException("Graph has many in-edges to map")
            val source_node = incoming.head.from.toOuter
            if (!(sizeMap contains source_node)) throw new UnsupportedOperationException("Cannot handle map with unknown size")
            sizeMap(d) = sizeMap(source_node)
            val sourceName = TermName(s"x$source_node")
            accel += q"val $varName = SRAM[${typeMap(m.parameter.tp.typeArgs.last)}](${sizeMap(d)})"
            accel += q"Foreach(${sizeMap(d)} by 1) {i => $varName(i) = ${m.parameter.tree}($sourceName(i)) }"

            locationMap(d) = accel

          case a: core.Aggregate =>
            val zeroValueType = a.outputTypes.head
            val zeroType = typeMap(zeroValueType.tpe)
            val zeroValue = toolbox.parse(s"$zeroType(${a.parameter._3.toString})")

            val incoming = programGraph.innerGraph.get(d).incoming
            if (incoming.size != 1) throw new IllegalArgumentException("Graph has many in-edges to aggregate")
            val source_node = incoming.head.from.toOuter
            if (!(sizeMap contains source_node)) throw new UnsupportedOperationException("Cannot handle aggregate with unknown size")
            val sourceName = TermName(s"x$source_node")

            accel += q"val $varName = Reg[$zeroType]($zeroValue)"
            accel +=
              q"""
                 Fold($varName)(${sizeMap(source_node)} by 1)
                  {i =>
                  val temp = Reg[$zeroType]($zeroValue)
                  temp := $sourceName(i)
                  temp
                  }
                  {${a.parameter._1.tree}}"""

            sizeMap(d) = 1
            locationMap(d) = accel

          case o: core.Output =>

            val path = o.parameter._1
            val suffix = o.parameter._2
            locationMap(d) = post

            val incoming = programGraph.innerGraph.get(d).incoming
            if (incoming.size != 1) throw new IllegalArgumentException("Graph has many in-edges to write")
            val source_node = incoming.head.from.toOuter
            if (!(sizeMap contains source_node)) throw new UnsupportedOperationException("Cannot handle write with unknown size")
            val sourceName = TermName(s"x$source_node")
            val dest = Literal(Constant(s"$path.$suffix"))

            var toBeCopied: Tree = null

            if (locationMap(source_node) eq accel) {
              // Output is always in the post block, so we need to do a copy
              val selftag = o.inputTypes.head
              val prevtag = programGraph.idMap(source_node).outputTypes.head
              var tp: Type = if (selftag.tpe <:< prevtag.tpe) selftag.tpe else prevtag.tpe

              val outTerm = TermName(s"x${d}tmp")
              pre += q"val $outTerm = ArgOut[${typeMap(tp)}]"
              accel += q"$outTerm := $sourceName"

              toBeCopied = q"$outTerm.toText"
            } else {
              toBeCopied = q"$sourceName.toText"
            }

            post += q"writeCSV1D[String](Array[String]($toBeCopied), $dest)"


          case _ =>
            throw new UnsupportedOperationException(s"Spatial backend does not support node $pattern")
        }
      }
    }

    val typeDefs = typeMap map {
      d =>
        if ((d._1.toString contains "java") || (d._2.toString contains "java")) q""
        else toolbox.parse(s"type ${d._1.toString} = ${d._2.toString}")
    } filterNot {_.isEmpty}

    val tree = q"""
         import argon.core._
         import spatial.dsl._
         import org.virtualized.{SourceContext, EmptyContext}

         implicit val sc: SourceContext = EmptyContext
         implicit val state: State = new State
         state.context = Nil
         val program = stageBlock({
           ..$typeDefs
           ..$pre
           Accel {
             ..$accel
           }
           ..$post
           argon.lang.Unit.const()
         })
         (state,program)
       """
    println(tree)
    toolbox.eval(tree).asInstanceOf[(State,argon.core.Block[_])]
  }

  def execute(sb: (State, argon.core.Block[_])): Unit = {
    val (s, b) = sb
    val compiler = new SpatialCompiler {
      override def stagingArgs: Array[String] = Array.empty
      __IR = s

      def compile(stageArgs: Array[String]): Unit = {
        passes.clear()                        // Reset traversal passes
        IR.config = createConfig()            // Create a new Config
        IR.config.name = name                 // Set the default program name
        IR.config.init()                      // Initialize the Config (from files)
        parseArguments(IR.config, stageArgs)  // Override config with any command line arguments
        settings()                            // Override config with any DSL or App specific settings
        createTraversalSchedule(IR)           // Set up the compiler schedule for the app

        val startTime = System.currentTimeMillis()
        checkBugs(startTime, "staging")
        checkErrors(startTime, "staging")

        val timingLog = createLog(IR.config.logDir, "9999 CompilerTiming.log")
        runTraversals(startTime, b.asInstanceOf[argon.core.Block[argon.lang.Unit]], timingLog)
      }
    }

    compiler.compile(Array.empty)
  }
}
