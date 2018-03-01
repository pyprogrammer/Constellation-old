package constellation.core.macros

import constellation.core.Pattern

import scala.language.experimental.macros
import scala.annotation.{StaticAnnotation, compileTimeOnly}
import scala.collection.mutable
import scala.reflect.macros.whitebox
import scala.reflect.runtime.universe.{Type => uType}

@compileTimeOnly("This should not be here")
final class pattern(args: Pattern[_]*) extends StaticAnnotation {
  def macroTransform(annottees: Any*): Any = macro pattern.impl
}

private object pattern {
  def impl(c: whitebox.Context)(annottees: c.Expr[Any]*): c.Expr[Any] = {
    import c.universe._
    val results = annottees map {_.tree} match {
      case (traitDef @ q"$mods trait $tpname[..$tparams] extends { ..$earlydefns } with ..$parents { $self => ..$stats }") :: _ =>
        println(showRaw(tpname))
        val tname = tpname match {
          case TypeName(s) => Literal(Constant(s))
        }
        q"""$mods trait $tpname[..$tparams] extends { ..$earlydefns } with ..$parents {
           $self => ..$stats
           override val name: String = $tname
         }"""
      case _ => c.abort(c.enclosingPosition, s"Patterns must be traits.")
    }

    println(results)

    c.Expr[Any](results)
  }
}
