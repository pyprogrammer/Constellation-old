package apps.beam


import beam_backend.{Beam, BeamBackend}
import breeze.linalg.{DenseVector, norm}
import com.spotify.scio._
import spatial_backend.SpatialBackend

@Beam
object VectorNorm extends App {

  val n = 2

  val a = DenseVector.rand[Double](n)
  println(a)
  println(norm(a))

  val (sc, arg) = ContextAndArgs(args)

  val p1 = sc.parallelize(a.toScalaVector())
  val p2 = p1 map {
    x => x * x
  }
  val p3 = p2.fold(0) {
    _ + _
  }

  p3.saveAsTextFile("output", "txt")
  sc.close()

  val graph = BeamBackend.extract(sc.pipeline)

  val state = SpatialBackend.implement(graph)
  SpatialBackend.execute[Any](state)
}

