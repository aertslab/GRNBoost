package org.aertslab.grnboost

import com.monovore.decline._

/**
  * @author Thomas Moerman
  */
object CLI extends CommandApp(

  name = "GRNBoost",
  header = "GRNBoost - a library for GRN inference",
  main = {

    val in = Opts.option[String]("in",
      short = "i",
      metavar = "input file",
      help = "Input file or directory.")

    val TF = Opts.option[String]("regulators",
      short = "tf",
      metavar = "regulators file",
      help = "File containing the candidate regulators.")

    val out = Opts.option[String]("out",
      short = "o",
      metavar = "output dir",
      help = "Output directory.")

    val delimiter = Opts.option[String]("delimiter",
      short = "d",
      metavar = "delimiter",
      help = "The delimiter for parsing the input and regulators files.")

    val boosterParams = Opts.option[String]("booster-params",
      short = "p",
      metavar = "params string",
      help = "A comma-delimited collection of key=value pairs for the XGB booster.")

    val boosterConfig = Opts.option[String]("booster-config",
      short = "c",
      metavar = "params file",
      help = "A properties file containing key=value pairs for the XGB booster.")

    val nrPartitions = Opts.option[Int]("nr-partitions",
      short = "np",
      metavar = "nr",
      help = "The number of Spark partitions to use for this job.")

    val nrBoosterThreads = Opts.option[Int]("nr-booster-threads",
      short = "bt",
      metavar = "nr",
      help = "The number of threads an individual XGB regression is allowed to use.")

    val nrBoostingRounds = Opts.option[Int]("nr-boosting-rounds",
      short = "br",
      metavar = "nr",
      help = "The number of rounds for every XGB regression.")

    ???
  }


)