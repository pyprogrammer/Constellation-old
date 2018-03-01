package patterns.core

import constellation.core.Pattern
import constellation.core.macros.{AnnotatedFunction, pattern}

import scala.reflect.runtime.universe
import scala.reflect.runtime.universe.Type

@pattern trait Map extends Pattern[AnnotatedFunction]

@pattern trait Zip extends Pattern[Unit]

@pattern trait Aggregate extends Pattern[(AnnotatedFunction, AnnotatedFunction, Any)]

@pattern trait Root extends Pattern[Unit]

@pattern trait Create extends Pattern[(Seq[Any], Type)]

@pattern trait Output extends Pattern[(String, String)]
