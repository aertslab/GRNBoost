package ml.dmlc.xgboost4j.scala

/**
  * @author Thomas Moerman
  */
object XGBoostAccess {

  def inner(m: DMatrix) = m.jDMatrix

}