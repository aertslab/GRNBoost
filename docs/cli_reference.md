[Home](../README.md) | [Installation Guide](installation.md) | [User Guide](user_guide.md) | [__Command Line Reference__](cli_reference.md) | [Developer Guide](developer_guide.md)

# Command Line Reference

```
Usage: GRNBoost [infer] [options]

  -h | --help
        
  Prints this usage text.
      
  -v | --version
        
  Prints the version number.
      
Command: infer [options]

  -i <file> | --input <file>
        
  REQUIRED. Input file or directory.
        
  -o <file> | --output <file>
        
  REQUIRED. Output directory.
        
  -tf <file> | --regulators <file>
        
  REQUIRED. Text file containing the regulators (transcription factors), one regulator per line.
        
  -skip <nr> | --skip-headers <nr>
        
  The number of input file header lines to skip. Default: 0.
        
  --delimiter <del>
        
  The delimiter to use in input and output files. Default: TAB.
        
  -s <nr> | --sample <nr>
        
  Use a sample of size <nr> of the observations to infer the GRN.
        
  --targets <gene1,gene2,gene3...>
        
  List of genes for which to infer the putative regulators.
        
  -p:<key>=<value> | --xgb-param:<key>=<value>
        
  Add or overwrite an XGBoost booster parameter. Default parameters are:
  * eta	->	0.01
  * max_depth	->	1
  * nthread	->	1
  * silent	->	1
          
  -r <nr> | --nr-boosting-rounds <nr>
        
  Set the number of boosting rounds. Default: heuristically determined nr of boosting rounds.
        
  --estimation-genes <gene1,gene2,gene3...>
        
  List of genes to use for estimating the nr of boosting rounds.
        
  --nr-estimation-genes <nr>
        
  Nr of randomly selected genes to use for estimating the nr of boosting rounds. Default: 20.
        
  --regularized
        
  Enable regularization (using the triangle method). Default: disabled
  When enabled, only regulations approved by the triangle method will be emitted.
  When disabled, all regulations will be emitted.
  Use the 'include-flags' option to specify whether to output the include flags in the result list.
        
  --normalized
        
 Enable normalization by dividing the gain scores of the regulations per target over the sum of gain scores.
 Default = disabled.
        
  --include-flags <true/false>
        
  Flag whether to output the regularization include flags in the output. Default: false.
        
  --truncate <nr>
        
  Only keep the specified number regulations with highest importance score. Default: unlimited.
  (Motivated by the 100.000 regulations limit for the DREAM challenges.)
        
  -par <nr> | --nr-partitions <nr>
        
  The number of Spark partitions used to infer the GRN. Default: nr of available processors.
        
  --dry-run
        
  Inference nor auto-config will launch if this flag is set. Use for parameters inspection.
        
  --cfg-run
        
  Auto-config will launch, inference will not if this flag is set. Use for config testing.
        
  --report <true/false>
        
  Set whether to write a report about the inference run to file. Default: true.
```