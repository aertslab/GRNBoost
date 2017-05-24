package org.aertslab.grnboost

import org.apache.spark.ml.Transformer
import org.apache.spark.ml.attribute.AttributeGroup
import org.apache.spark.ml.feature.VectorSlicer
import org.apache.spark.ml.linalg.{Vector => MLVector}
import org.apache.spark.ml.param.{IntArrayParam, IntParam, ParamMap}
import org.apache.spark.ml.util.{DefaultParamsWritable, Identifiable}
import org.apache.spark.sql.functions._
import org.apache.spark.sql.types.{DoubleType, StructField, StructType}
import org.apache.spark.sql.{DataFrame, Dataset}

/**
  * A Spark ML transformer for slicing the regression data from the full data set.
  *
  * @author Thomas Moerman
  */
class SliceRegressionData(override val uid: String)
  extends Transformer with DefaultParamsWritable {

  val targetGene = new IntParam(this, "targetGene", "target gene index")

  val candidateRegulators = new IntArrayParam(this, "candidateRegulators", "indices of the candidate regulators")

  def this() = this(Identifiable.randomUID(getClass.getSimpleName))

  override def transform(data: Dataset[_]): DataFrame = {
    val targetSlicer =
      new VectorSlicer()
        .setInputCol(EXPRESSION)
        .setOutputCol(TARGET_GENE)
        .setIndices(Array($(targetGene)))

    val actualRegulators = $(candidateRegulators).filter(_ != $(targetGene))

    val predictorSlicer =
      new VectorSlicer()
        .setInputCol(EXPRESSION)
        .setOutputCol(REGULATORS)
        .setIndices(actualRegulators)

    val toScalar = udf[Double, MLVector](_.apply(0))

    Some(data)
      .map(targetSlicer.transform)
      .map(predictorSlicer.transform)
      .map(df => df.withColumn(TARGET_GENE, toScalar(df.apply(TARGET_GENE))))
      .map(df => df.select(REGULATORS, TARGET_GENE))
      .get
  }

  override def transformSchema(schema: StructType): StructType = {
    require($(candidateRegulators).length > 0, "specify at least one candidate regulator")

    if (! schema.fieldNames.contains(EXPRESSION)) {
      throw new IllegalArgumentException(s"Input schema should contain $EXPRESSION")
    }

    val regulators = new AttributeGroup(REGULATORS).toStructField()

    val target = StructField(TARGET_GENE, DoubleType, nullable = false)

    StructType(Seq(target, regulators))
  }

  override def copy(extra: ParamMap): SliceRegressionData = defaultCopy(extra)

}
