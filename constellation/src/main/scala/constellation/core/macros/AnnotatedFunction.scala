package constellation.core.macros

trait AnnotatedFunction {
  import scala.reflect.runtime.universe
  import scala.tools.reflect.ToolBox

  def funcString: String

  private lazy val toolbox = universe.runtimeMirror(universe.getClass.getClassLoader).mkToolBox()

  lazy val tree: universe.Tree = {
    toolbox.parse(funcString)
  }

  lazy val typeCheckedTree: universe.Tree = {
    toolbox.typecheck(toolbox.parse(funcString))
  }

  lazy val tp: universe.Type = {
    typeCheckedTree.tpe
  }
}
