package beam_backend


import breeze.linalg.{DenseVector, norm}
import com.spotify.scio._

object VectorNorm extends App {

  override def main(args: Array[String]): Unit = {
    val n = 100

    val a = DenseVector.rand[Double](n)
    println(a)
    println(norm(a))

    val (sc, arg) = ContextAndArgs(args)

    val p1 = sc.parallelize(a.toScalaVector())
    val p2 = p1.map {
      scala.math.pow(_, 2)
    }
    val p3 = p2.fold(0) {
      _ + _
    }
    val p4 = p3.map {
      scala.math.pow(_, 0.5)
    }
    p4.saveAsTextFile("output", "txt")
    BeamBackend.extract(sc.pipeline)
    sc.close()


  }
}

