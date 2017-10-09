package org.aertslab.grnboost

import com.softwaremill.quicklens._
import org.aertslab.grnboost.DataReader.DEFAULT_MISSING
import scopt.OptionParser
import scopt.RenderingMode.OneColumn

import scala.util.Try

/**
  * Command line argument parser.
  * @author Thomas Moerman
  */
object CLI extends OptionParser[Config]("GRNBoost") {

  private val input =
    opt[Path]("input").abbr("i")
      .required
      .valueName("<file>")
      .text(
        """
          |  REQUIRED. Input file or directory.
        """.stripMargin)
      .action{ case (file, cfg) => cfg.modify(_.xgb.each.inputPath).setTo(Some(file)) }

  private val output =
    opt[Path]("output").abbr("o")
      .required
      .valueName("<file>")
      .text(
        """
          |  REQUIRED. Output directory.
        """.stripMargin)
      .action{ case (file, cfg) => cfg.modify(_.xgb.each.outputPath).setTo(Some(file)) }

  private val regulators =
    opt[Path]("regulators").abbr("tf")
      .required
      .valueName("<file>")
      .text(
        """
          |  REQUIRED. Text file containing the regulators (transcription factors), one regulator per line.
        """.stripMargin)
      .action{ case (file, cfg) => cfg.modify(_.xgb.each.regulatorsPath).setTo(Some(file)) }

  private val skipHeaders =
    opt[Int]("skip-headers").abbr("skip")
      .optional
      .valueName("<nr>")
      .text(
        """
          |  The number of input file header lines to skip. Default: 0.
        """.stripMargin)
      .action{ case (nr, cfg) => cfg.modify(_.xgb.each.skipHeaders).setTo(nr) }

  private val delimiter =
    opt[String]("delimiter")
      .optional
      .valueName("<del>")
      .text(
        """
          |  The delimiter to use in input and output files. Default: TAB.
        """.stripMargin)
      .action{ case (del, cfg) => cfg.modify(_.xgb.each.delimiter).setTo(del) }

  private val outputFormat =
    opt[String]("output-format")
      .optional
      .hidden // TODO implement this functionality
      .valueName("<list|matrix|parquet>")
      .validate(string =>
        Try(Format(string))
          .map(_ => success)
          .getOrElse(failure(s"unknown output format (${string.toLowerCase}")))
      .text(
        """
          |  Output format. Default: list.
        """.stripMargin)
      .action{ case (format, cfg) => cfg.modify(_.xgb.each.outputFormat).setTo(Format.apply(format)) }

  private val sample =
    opt[Double]("sample").abbr("s")
      .optional
      .valueName("<nr>")
      .validate(nr => if (nr <= 0) failure("sample must be > 0") else success)
      .text(
        """
          |  Use a sample of size <nr> of the observations to infer the GRN.
        """.stripMargin)
      .action{ case (nr, cfg) => cfg.modify(_.xgb.each.sampleSize.each).setTo(nr.toInt) }

  private val targets =
    opt[Seq[Gene]]("targets")
      .optional
      .valueName("<gene1,gene2,gene3...>")
      .text(
        """
          |  List of genes for which to infer the putative regulators.
        """.stripMargin)
      .action{ case (genes, cfg) => cfg.modify(_.xgb.each.targets).setTo(genes.toSet) }

  private val xgbParam =
    opt[(String, String)]("xgb-param").abbr("p")
      .optional
      .unbounded
      .text(
        s"""
           |  Add or overwrite an XGBoost booster parameter. Default parameters are:
           |${DEFAULT_BOOSTER_PARAMS.toSeq.sortBy(_._1).map{ case (k, v) => s"  * $k\t->\t$v" }.mkString("\n")}
          """.stripMargin)
      .action{ case ((key, value), cfg) => cfg.modify(_.xgb.each.boosterParams).using(_.updated(key, value)) }

  private val nrBoostingRounds =
    opt[Int]("nr-boosting-rounds").abbr("r")
      .optional
      .valueName("<nr>")
      .text(
        """
          |  Set the number of boosting rounds. Default: heuristically determined nr of boosting rounds.
        """.stripMargin)
      .action{ case (nr, cfg) => cfg.modify(_.xgb.each.nrBoostingRounds).setTo(Some(nr)) }

  private val estimationGenes =
    opt[Seq[Gene]]("estimation-genes")
      .optional
      .valueName("<gene1,gene2,gene3...>")
      .validate(genes => if (genes.nonEmpty) success else failure("estimation-genes cannot be empty"))
      .text(
        """
          |  List of genes to use for estimating the nr of boosting rounds.
        """.stripMargin)
      .action{ case (genes, cfg)  => cfg.modify(_.xgb.each.estimationSet).setTo(Right(genes.toSet))}

  private val nrEstimationGenes =
    opt[Int]("nr-estimation-genes")
      .optional
      .valueName("<nr>")
      .validate(nr => if (nr > 0) success else failure(s"nr-estimation-genes ($nr) should be larger than 0"))
      .text(
        s"""
          |  Nr of randomly selected genes to use for estimating the nr of boosting rounds. Default: $DEFAULT_ESTIMATION_SET.
        """.stripMargin)
      .action{ case (nr, cfg) => cfg.modify(_.xgb.each.estimationSet).setTo(Left(nr)) }

  private val regularized =
    opt[Unit]("regularized")
      .optional
      .text(
        """
          |  Enable regularization (using the triangle method). Default: disabled
          |  When enabled, only regulations approved by the triangle method will be emitted.
          |  When disabled, all regulations will be emitted.
          |  Use the 'include-flags' option to specify whether to output the include flags in the result list.
        """.stripMargin)
      .action{ case (_, cfg) => cfg.modify(_.xgb.each.regularized).setTo(true) }

  private val normalized =
    opt[Unit]("normalized")
      .optional
      .text(
        """
          | Enable normalization by dividing the gain scores of the regulations per target over the sum of gain scores.
          | Default = disabled.
        """.stripMargin)
      .action{ case (_, cfg) => cfg.modify(_.xgb.each.normalized).setTo(true) }

  private val includeFlags =
    opt[Boolean]("include-flags")
      .optional
      .valueName("<true/false>")
      .text(
        """
          |  Flag whether to output the regularization include flags in the output. Default: false.
        """.stripMargin)
      .action{ case (include, cfg) => cfg.modify(_.xgb.each.includeFlags).setTo(include) }

  private val truncate =
    opt[Int]("truncate")
      .optional
      .valueName("<nr>")
      .text(
        """
          |  Only keep the specified number regulations with highest importance score. Default: unlimited.
          |  (Motivated by the 100.000 regulations limit for the DREAM challenges.)
        """.stripMargin)
      .action{ case (nr, cfg) => cfg.modify(_.xgb.each.truncated).setTo(Some(nr)) }

  private val nrPartitions =
    opt[Int]("nr-partitions").abbr("par")
      .optional
      .valueName("<nr>")
      .text(
        """
          |  The number of Spark partitions used to infer the GRN. Default: nr of available processors.
        """.stripMargin)
      .action{ case (nr, cfg) => cfg.modify(_.xgb.each.nrPartitions).setTo(Some(nr)) }

  private val dryRun =
    opt[Unit]("dry-run")
      .optional
      .text(
        """
          |  Inference nor auto-config will launch if this flag is set. Use for parameters inspection.
        """.stripMargin)
      .action{ case (_, cfg) => cfg.modify(_.xgb.each.runMode).setTo(DRY_RUN) }

  private val configRun =
    opt[Unit]("cfg-run")
      .optional
      .text(
        """
          |  Auto-config will launch, inference will not if this flag is set. Use for config testing.
        """.stripMargin)
      .action{ case (_, cfg) => cfg.modify(_.xgb.each.runMode).setTo(CFG_RUN) }

  private val report =
    opt[Boolean]("report")
      .optional
      .valueName("<true/false>")
      .text(
        """
          |  Set whether to write a report about the inference run to file. Default: true.
        """.stripMargin)
      .action{ case (bool, cfg) => cfg.modify(_.xgb.each.report).setTo(bool) }

  private val iterated =
    opt[Unit]("iterated")
      .optional
      .hidden
      .text(
        """
          |  Indicates using the iterated DMatrix API instead of using cached DMatrix copies of the CSC matrix.
        """.stripMargin)
      .action{ case (_, cfg) => cfg.modify(_.xgb.each.iterated).setTo(true) }

  head("GRNBoost", "0.1")

  help("help").abbr("h")
    .text(
      """
        |  Prints this usage text.
      """.stripMargin)

  version("version").abbr("v")
    .text(
      """
        |  Prints the version number.
      """.stripMargin)

  cmd("infer")
    .action{ (_, cfg) => cfg.copy(xgb = Some(XGBoostConfig())) }
    .children(
      input, skipHeaders, output, regulators, delimiter, outputFormat, sample, targets,
      xgbParam, regularized, normalized, includeFlags, truncate, nrBoostingRounds, nrPartitions,
      estimationGenes, nrEstimationGenes, iterated, dryRun, configRun, report)

  override def renderingMode = OneColumn

  override def terminate(exitState: Either[String, Unit]): Unit = ()

  def apply(args: String*): Option[Config] = parse(args, Config())

  def parse(args: Array[String]): Option[Config] = apply(args: _*)

}

trait ConfigLike extends Product {

  override def toString: String = this.toMap.mkString("\n")

}

case class Config(xgb: Option[XGBoostConfig] = None) extends ConfigLike

/**
  * Trait representing the GRNBoost run mode.
  */
sealed trait RunMode

/**
  * Only inspect the configuration parameters. No boosting rounds estimation nor inference is performed.
  */
case object DRY_RUN extends RunMode

/**
  * Perform estimation of the number of boosting rounds if necessary. Do not perform actual inference.
  */
case object CFG_RUN extends RunMode

/**
  * Perform estimation of the number of boosting rounds if necessary. Update the inference parameters with estimated
  * number of boosting rounds value and perform the gene regulatory network inference.
  */
case object INF_RUN extends RunMode

sealed trait Format
case object LIST    extends Format
case object MATRIX  extends Format
case object PARQUET extends Format

object Format {

  def apply(s: String): Format = s.toLowerCase match {
    case "list"    => LIST
    case "matrix"  => MATRIX
    case "parquet" => PARQUET
    case _         => ???
  }

}

/**
  * @param inputPath Required. The input file.
  * @param regulatorsPath File containing regulator genes. Expects on gene per line.
  * @param outputPath Required. The output file.
  * @param skipHeaders The number of header lines to ignore in the input file.
  * @param delimiter The delimiter used to parse the input file. Default: TAB.
  * @param outputFormat The output format: list, matrix or parquet.
  * @param sampleSize The nr of randomly sampled cells to take into account in the inference.
  * @param nrPartitions The nr of Spark partitions to use for inference.
  * @param truncated The max nr of regulatory connections to return.
  * @param nrBoostingRounds The nr of boosting rounds.
  * @param estimationSet A nr or set of genes to estimate the nr of boosting rounds, if no nr is specified.
  * @param nrFolds The nr of folds to use to estimate the nr of boosting rounds with cross validation.
  * @param regularized Use triangle cutoff to prune the inferred regulatory connections.
  * @param normalized Divide gain scores by the sum of the gain scores.
  * @param includeFlags When true, the regularization include flags are also emitted in the output list.
  * @param targets A Set of target genes to infer the regulators for. Defaults to all.
  * @param boosterParams Booster parameters.
  * @param runMode The goal: dry-run, configuration or inference.
  * @param report Write a report to file.
  * @param iterated Hidden, experimental. Use iterated DMatrix initialization instead of copying.
  */
case class XGBoostConfig(inputPath:         Option[Path]            = None,
                         regulatorsPath:    Option[Path]            = None,
                         outputPath:        Option[Path]            = None,
                         skipHeaders:       Int                     = 0,
                         delimiter:         String                  = "\t",
                         outputFormat:      Format                  = LIST,
                         sampleSize:        Option[Int]             = None,
                         nrPartitions:      Option[Int]             = None,
                         truncated:         Option[Int]             = None,
                         nrBoostingRounds:  Option[Int]             = None,
                         estimationSet:     Either[Int, Set[Gene]]  = Left(DEFAULT_ESTIMATION_SET),
                         nrFolds:           Int                     = DEFAULT_NR_FOLDS,
                         regularized:       Boolean                 = false,
                         normalized:        Boolean                 = false,
                         includeFlags:      Boolean                 = false,
                         targets:           Set[Gene]               = Set.empty,
                         boosterParams:     BoosterParams           = DEFAULT_BOOSTER_PARAMS,
                         runMode:           RunMode                 = INF_RUN,
                         report:            Boolean                 = true,
                         iterated:          Boolean                 = false,
                         missing:           Set[Double]             = DEFAULT_MISSING) extends ConfigLike
