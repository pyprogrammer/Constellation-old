package beam_backend

import constellation.core.macros.FuncAnnotator

import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.language.experimental.macros
import scala.reflect.macros.whitebox.Context

@compileTimeOnly("This should not be here")
final class Beam extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro Beam.impl
}

private object Beam {

  var funcCounter = 0

  def impl(c: Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._

    println("Beaming")

    val funcTransformer = FuncAnnotator.createAnnotator(c)

    val results = annottees map (_.tree) match {
      case (objectDef @ q"$mods object $tname extends { ..$earlydefns } with ..$parents { $self => ..$stats }") :: _ =>
        c.untypecheck(funcTransformer.transform(c.typecheck(objectDef)))
      // Not an object.
      case _ => c.abort(c.enclosingPosition, "Invalid annotation target: not an object")
    }
    println(results)
    println(showRaw(results))
    c.Expr[Any](results)
  }
}
