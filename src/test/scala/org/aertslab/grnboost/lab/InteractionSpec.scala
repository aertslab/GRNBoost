package org.aertslab.grnboost.lab

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import org.apache.spark.ml.feature.{Interaction, VectorAssembler}
import org.scalatest.{Matchers, FlatSpec}

/**
  * @author Thomas Moerman
  */
class InteractionSpec extends FlatSpec with DataFrameSuiteBase with Matchers {

  "it" should "illustrate how interactions work" in {

    val df = spark.createDataFrame(Seq(
      (1, 1, 2, 3, 8, 4, 5),
      (2, 4, 3, 8, 7, 9, 8),
      (3, 6, 1, 9, 2, 3, 6),
      (4, 10, 8, 6, 9, 4, 5),
      (5, 9, 2, 7, 10, 7, 3),
      (6, 1, 1, 4, 2, 8, 4)
    )).toDF("id1", "id2", "id3", "id4", "id5", "id6", "id7")

    val assembler1 =
      new VectorAssembler()
        .setInputCols(Array("id2", "id3", "id4"))
        .setOutputCol("vec1")

    val assembled1 = assembler1.transform(df)

    val assembler2 =
      new VectorAssembler()
        .setInputCols(Array("id5", "id6", "id7"))
        .setOutputCol("vec2")

    val assembled2 = assembler2.transform(assembled1).select("id1", "vec1", "vec2")

    val interaction =
      new Interaction()
        .setInputCols(Array("id1", "vec1", "vec2"))
        .setOutputCol("interactedCol")

    val interacted = interaction.transform(assembled2)

    interacted.show(truncate = false)

  }

}