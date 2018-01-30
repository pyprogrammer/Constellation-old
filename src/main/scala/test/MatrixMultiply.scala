package test

import breeze.linalg.{DenseMatrix, pinv}
import com.spotify.scio._

object MatrixMultiply extends App {


  override def main(args: Array[String]): Unit = {
    val blockSize = 2
    val n = 4

    val a = DenseMatrix.rand[Double](n, n)
    val b = pinv(a)

    println(blockSize)

    val blockedA = (0 until n/blockSize).map(x => (x, a(x*blockSize until (x+1) * blockSize, ::)))
    val blockedB = (0 until n/blockSize).map(x => (x, b(::, x*blockSize until (x+1) * blockSize)))

    val (sc, arg) = ContextAndArgs(args)

    val isb = sc.parallelize(blockedB)

    sc.parallelize(blockedA).cross(isb).map {
      d => ((d._1._1, d._2._1), d._1._2 * d._2._2)
    }.aggregate(Map[(Int, Int), DenseMatrix[Double]]())((m, d) => m + d, (m1, m2) => {m1 ++ m2}).saveAsTextFile("output")

    sc.close()
  }
}
