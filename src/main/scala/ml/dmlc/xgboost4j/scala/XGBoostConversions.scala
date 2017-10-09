package ml.dmlc.xgboost4j.scala

import ml.dmlc.xgboost4j.java.{Booster => JBooster, DMatrix => JDMatrix}
import org.aertslab.grnboost.BoosterParams

import scala.collection.JavaConverters._

/**
  * @author Thomas Moerman
  */
object XGBoostConversions {

  implicit def toInner(m: DMatrix) = m.jDMatrix

  implicit def toWrapper(m: JDMatrix) = new DMatrix(m)

  implicit def toWrapper(b: JBooster) = new Booster(b)

  implicit def coerced(params: BoosterParams) = params.mapValues(_.toString).toMap[String, Object].asJava

}