package org.aertslab.grnboost

import java.io.ByteArrayOutputStream

import org.scalatest.{FlatSpec, Matchers}

import scala.Console._

/**
  * @author Thomas Moerman
  */
class CLISpec extends FlatSpec with Matchers {

  CLI("--help")

  behavior of "CommandLine argument parsing"

  it should "Print an error when no command is specified" in {
    val captured = new ByteArrayOutputStream
    withErr(captured) {
      CLI() shouldBe Some(Config(None))
    }

    println(captured.toString)
  }

  it should "Complain when the 'infer' command is specified with insufficient params are specified" in {
    val captured = new ByteArrayOutputStream
    withErr(captured) {
      CLI("infer") shouldBe None
    }
    captured.toString should startWith ("Error: Missing option")
  }

  it should "Print help" in {
    val captured = new ByteArrayOutputStream
    withOut(captured) {
      CLI("--help") shouldBe Some(Config(None))
    }
    captured.toString should startWith ("GRNBoost 0.1")
  }

  it should "not complain when required arguments are provided" in {
    val args = Array(
      "infer",
      "-i", "src/test/resources/genie3/data.txt",
      "-tf", "src/test/resources/TF/mm9_TFs.txt",
      "-o", "src/test/resources/genie3/out.txt",
      "--dry-run")

    val parsed = CLI.parse(args).get.xgb.get

    parsed.runMode            shouldBe DRY_RUN
  }

  it should "capture target genes" in {
    val args = Array(
      "infer",
      "-i", "src/test/resources/genie3/data.txt",
      "-tf", "src/test/resources/TF/mm9_TFs.txt",
      "-o", "src/test/resources/genie3/out.txt",
      "--targets", "Tspan2,Dlx1,Neurod2")

    val parsed = CLI.parse(args).get.xgb.get

    parsed.targets shouldBe Set("Tspan2", "Dlx1", "Neurod2")
  }

  it should "capture XGBoost params" in {
    val args = Array("infer",
      "-i", "src/test/resources/genie3/data.txt",
      "-tf", "src/test/resources/TF/mm9_TFs.txt",
      "-o", "src/test/resources/genie3/out.txt",
      "-p", "seed=777")

    val parsed = CLI.parse(args).get.xgb.get

    parsed.boosterParams shouldBe DEFAULT_BOOSTER_PARAMS + (XGB_SEED -> "777")
  }

}