package org.aertslab.grnboost

import java.io.File

//import org.aertslab.grnboost.util.PropsReader.props
import org.apache.commons.io.FileUtils.cleanDirectory
import org.scalatest.tagobjects.Slow
import org.scalatest.{FlatSpec, Matchers}

import scala.io.Source

/**
  * @author Thomas Moerman
  */
class GRNBoostRunSpec extends FlatSpec with GRNBoostSuiteBase with Matchers {

  "dry-run" should "run" taggedAs Slow in {
    val args = Array(
      "infer",
      "-i", "src/test/resources/genie3/data.txt",
      "-tf", "src/test/resources/TF/mm9_TFs.txt",
      "-o", "src/test/resources/genie3/out.meh",
      "--dry-run")

    val inferenceCfg = CLI.parse(args).get.xgb.get

    val (_, params) = GRNBoost(inferenceCfg)

    inferenceCfg.runMode shouldBe DRY_RUN

    println(params)
  }

//  FIXME -> replace with DREAM5 test files
//
//  val zeiselFiltered = props("zeiselFiltered")
//
//  "cfg-run" should "pass smoke test" taggedAs Slow in {
//    val args = Array(
//      "infer",
//      "-i", zeiselFiltered, "--skip-headers", "1",
//      "-tf", "src/test/resources/TF/mm9_TFs.txt",
//      "-o", "src/test/resources/zeisel/out.meh",
//      "--nr-estimation-genes", "2",
//      "--cfg-run")
//
//    val inferenceCfg = CLI.parse(args).get.xgb.get
//
//    val (updatedCfg, params) = GRNBoost(inferenceCfg)
//
//    updatedCfg.estimationSet.right.get.size shouldBe 2
//  }
//
//  "run" should "pass smoke for 1 target" taggedAs Slow in {
//    val outDir  = "src/test/resources/zeisel"
//    val outPath = outDir + "/out.regularized"
//    val out  = new File(outPath)
//
//    cleanDirectory(new File(outDir))
//
//    val args = Array(
//      "infer",
//      "-i", zeiselFiltered, "--skip-headers", "1",
//      "-tf", "src/test/resources/TF/mm9_TFs.txt",
//      "-o", outPath,
//      "--nr-estimation-genes", "2",
//      "--regularized",
//      "--normalized",
//      "--targets", "Gad1")
//
//    val inferenceCfg = CLI.parse(args).get.xgb.get
//
//    inferenceCfg.regularized shouldBe true
//
//    val (updatedCfg, _) = GRNBoost(inferenceCfg)
//
//    updatedCfg.estimationSet.right.get.size shouldBe 2
//
//    val lines = Source.fromFile(outPath + ".result.tsv").getLines.toList
//
//    lines.size shouldBe 13
//
//    println(lines.mkString("\n"))
//  }
//
//  "run" should "pass smoke for 1 target, iterated" taggedAs Slow in {
//
//    val outDir  = "src/test/resources/zeisel"
//    val outPath = outDir + "/out.regularized"
//    val out  = new File(outPath)
//
//    cleanDirectory(new File(outDir))
//
//    val args = Array(
//      "infer",
//      "-i", zeiselFiltered, "--skip-headers", "1",
//      "-tf", "src/test/resources/TF/mm9_TFs.txt",
//      "-o", outPath,
//      "--iterated",
//      "--nr-estimation-genes", "2",
//      "--regularized",
//      "--normalized",
//      "--targets", "Gad1")
//
//    val inferenceCfg = CLI.parse(args).get.xgb.get
//
//    inferenceCfg.regularized shouldBe true
//
//    val (updatedCfg, _) = GRNBoost(inferenceCfg)
//
//    updatedCfg.estimationSet.right.get.size shouldBe 2
//
//    val lines = Source.fromFile(outPath + ".result.tsv").getLines.toList
//
//    lines.size shouldBe 13
//
//    println(lines.mkString("\n"))
//
//    cleanDirectory(new File(outDir))
//  }

}