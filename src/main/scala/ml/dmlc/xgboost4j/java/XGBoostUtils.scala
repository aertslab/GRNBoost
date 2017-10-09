package ml.dmlc.xgboost4j.java

import java.io.ByteArrayInputStream

import ml.dmlc.xgboost4j.java.{Booster => JBooster}
import ml.dmlc.xgboost4j.scala.XGBoostConversions._
import ml.dmlc.xgboost4j.scala.{DMatrix => SDMatrix}
import org.aertslab.grnboost.BoosterParams

/**
  * @author Thomas Moerman
  */
object XGBoostUtils {

  def createBooster(params: BoosterParams, train: SDMatrix, test: SDMatrix): JBooster =
    new JBooster(params, Array(train, test))

  def createBooster(params: BoosterParams, train: SDMatrix): JBooster =
    new JBooster(params, Array(train))

  def loadBooster(bytes: Array[Byte], params: BoosterParams): JBooster = {
    val in = new ByteArrayInputStream(bytes)

    val booster = JBooster.loadModel(in)
    booster.setParams(params)

    in.close
    booster
  }

}