import argon.core.State
import org.virtualized.{EmptyContext, SourceContext}
import spatial.lang.Reg

object O extends App {
  implicit val state: State = new State
  implicit val source: SourceContext = EmptyContext
  state.context = Nil
  val temp = Reg[argon.lang.FixPt[argon.lang.typeclasses.TRUE,argon.lang.typeclasses._32,argon.lang.typeclasses._32]](argon.lang.FixPt[argon.lang.typeclasses.TRUE,argon.lang.typeclasses._32,argon.lang.typeclasses._32](0.0))
}