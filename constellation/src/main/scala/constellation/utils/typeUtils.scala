package constellation.utils

import scala.reflect.runtime.universe._
import scala.reflect.api

import scala.tools.reflect.ToolBox


object typeUtils {
  private val mirror = runtimeMirror(getClass.getClassLoader)
  private lazy val toolbox = mirror.mkToolBox()

  def getGenericTypes[T: TypeTag](value: T): List[Type] = {
    typeOf[T].typeArgs
  }

  def classToType[T](clazz: Class[T]): Type = mirror.classSymbol(clazz).toType

  def getAttribute[T](obj: AnyRef, path: Seq[String]): T = {
    var o = obj
    for (name <- path) {
      val cl = o.getClass
      val field = cl.getDeclaredField(name)
      field.setAccessible(true)
      o = field.get(o)
    }
    o.asInstanceOf[T]
  }

  def typeToTag[T](tpe: Type): TypeTag[T] =
    TypeTag(mirror, new api.TypeCreator {
      def apply[U <: api.Universe with Singleton](m: api.Mirror[U]) =
        if (m eq mirror) tpe.asInstanceOf[U # Type]
        else throw new IllegalArgumentException(s"Type tag defined in $mirror cannot be migrated to other mirrors.")
    })

  def createNestedTypeTag(containerType: Type, innerType: Type): TypeTag[_] = {
    val ttagCall = s"scala.reflect.runtime.universe.typeTag[${containerType.typeConstructor}[$innerType]]"
    val tpe = toolbox.typecheck(toolbox.parse(ttagCall), toolbox.TYPEmode).tpe.resultType.typeArgs.head

    TypeTag(mirror, new reflect.api.TypeCreator {
      def apply[U <: reflect.api.Universe with Singleton](m: reflect.api.Mirror[U]) = {
        assert(m eq mirror, s"TypeTag[$tpe] defined in $mirror cannot be migrated to $m.")
        tpe.asInstanceOf[U#Type]
      }
    })
  }

  def cast[A](a: Any, tt: TypeTag[A]): A = a.asInstanceOf[A]

}
