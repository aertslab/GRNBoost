[Home](../README.md) | [Installation Guide](installation.md) | [__User Guide__](user_guide.md) | [Command Line Reference](cli_reference.md) | [Developer Guide](developer_guide.md)

# User Guide

1. [Anatomy of a GRNBoost Job](#1-anatomy-of-a-GRNBoost-job)
2. [File Format Conventions](#2-file-format-conventions)
3. [Running GRNBoost](#3-running-grnboost)

## 1 Anatomy of a GRNBoost Job

GRNBoost is an [Apache Spark](http://spark.apache.org/) Application library. A Spark application entails a program, bundled as a [.jar file](https://en.wikipedia.org/wiki/JAR_(file_format)), that can be launched by [submitting](http://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit) it to a Spark instance using the command line. Let's have a look at an example:

> NOTE: the backslashes are used for multiline bash shell commands

```bash
$SPARK_HOME/bin/spark-submit \
    --class org.aertslab.grnboost.GRNBoost \
    --master local[*] \
    --deploy-mode client \    
    --jars /home/xxx/.m2/repository/ml/dmlc/xgboost4j/0.7/xgboost4j-0.7.jar \
    /path/to/GRNBoost.jar \
    infer \    
    -i  /path/to/dream5/training\ data/Network\ 1\ -\ in\ silico/net1_expression_data.transposed.tsv \
    -tf /path/to/dream5/training\ data/Network\ 1\ -\ in\ silico/net1_transcription_factors.tsv \
    -o  /path/to/grnboost/output/net1_grnboost_depth3.tsv \
    -p eta=0.01 \
    -p max_depth=3 \
    -p colsample_bytree=0.1 \
    --truncate 100000
```

A GRNBoost job command consists of 4 different parts:

1. Call the `spark-submit` executable.

    ```bash
    $SPARK_HOME/bin/spark-submit \
    ```

2. Specify the Spark command line arguments. Consult to the Spark [Submitting Applications](https://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit) page for detailed information.

    ```bash
    --class org.aertslab.grnboost.GRNBoost \        
    --master local[*] \
    --deploy-mode client \
    --jars /path/to/xgboost4j-0.7.jar \     
    ```

    Notice that in the last line, we specify the location of the xgboost .jar file we already [built or downloaded](installation.md). We chose not to include xgboost by default in GRNBoost because it is built differently on different platforms (MaxOS, Unbuntu, ...). Instead we refer to it as an additional `.jar` file in the Spark job.

3. Specify the path to the Spark Application `.jar` file, in this case the GRNBoost.jar artifact.

    ```bash
    /path/to/GRNBoost.jar \    
    ```

4. Specify the GRNBoost command line arguments. Consult the [Command Line Reference](cli_reference.md) for detailed information.

    ```bash
    infer \    
    -i  /media/tmo/data/work/datasets/dream5/training\ data/Network\ 1\ -\ in\ silico/net1_expression_data.transposed.tsv \
    -tf /media/tmo/data/work/datasets/dream5/training\ data/Network\ 1\ -\ in\ silico/net1_transcription_factors.tsv \
    -o  /media/tmo/data/work/datasets/dream5/grnboost/net1/net1_grnboost_depth3.tsv \
    -p eta=0.01 \
    -p max_depth=3 \
    -p colsample_bytree=0.1 \        
    --truncate 100000
    ```

    GRNBoost parameters are typically:
    * the input file containing the expression matrix
    * the file containing the list of transcription factors
    * the output file name
    * some optional [xgboost-specific parameters](https://xgboost.readthedocs.io/en/latest//parameter.html) for controlling regression behaviour
    * parameters for post-processing the collection of inferred regulatory links between candidate regulators and target genes

## 2 File Format Conventions

### 2.1 Input File Format

GRNBoost accepts text files with following layout. Each non-header line starts with a gene name and its expression profile across the observations. The [CLI](cli-reference.md) provides an option to skip header lines.

```bash
header etc.                                         # <-- unused header line
GENE        obs1    obs2    obs3    obs4    obs5    # <-- unused header line
Tspan12     0       0.666   0       0       0.089   # gene + expression profile     
Gad1        1.800   0       0       0.061   0       # gene + expression profile
Neurod1     0       0       1.301   0.232   0       # gene + expression profile
...
...
#       ^       ^       ^       ^       ^
#       expression profile matrix from second to last column
#
#  ^
#  first column contains gene name

```

### 2.2 Output file format

GRNBoost writes the inferred gene regulatory network to file as lines of regulator, target and importance. For example:

```

TF1     target1     0.234
TF2     target7     0.225
TF10    target2     0.201
...
...
```

## 3 Running GRNBoost

Instructions on setting up a Spark cluster are out of scope for this manual, please consult the Apache Spark [cluster overview documention](http://spark.apache.org/docs/latest/cluster-overview.html).

We will treat two usages scenarios: running on a local machine and running on Amazon Elastic MapReduce.

### 3.1 Local Mode

Although Spark was designed to run on a multi-node compute cluster, it is also capable to make good use of the resources of a (preferably powerful) computer with one or more physical CPUs, like a single cluster node. In this case we can simply install Spark in a local folder and [submit](http://spark.apache.org/docs/latest/submitting-applications.html#launching-applications-with-spark-submit) GRNBoost as a Spark job to that instance.

GRNBoost was developed against Spark `2.1.0`, so any version equal or higher than that will do nicely. We recommend downloading the default suggested release from the [Spark downloads page](http://spark.apache.org/downloads.html).

1. [Download a Spark release](http://spark.apache.org/downloads.html) and unpack it somewhere.
2. Add the `SPARK_HOME` environment variable, specifying the location where you unpacked the Spark release, to your `~/.bash_profile` or `~/.bashrc` file. For example:

    ```
    export SPARK_HOME=~/<..folders../..here..>/spark-2.0.2-bin-hadoop2.7
    ```

3. Now we can submit a GRNBoost Spark job via the command line. We recommend putting the GRNBoost command in a file and making that file executable: `chmod +x <grnboost_command.sh>`.

    Make sure you have [obtained or built](installation.md) the GRNBoost and xgboost artifacts, we refer to those in the job submit command. The job described  [above](#1-anatomy-of-a-GRNBoost-job) is a local mode job.

    Observe the third line in the command:

    ```bash
    $SPARK_HOME/bin/spark-submit \
        --class org.aertslab.grnboost.GRNBoost \
        --master local[*] \
        ...
    ```

    The [master URL](http://spark.apache.org/docs/latest/submitting-applications.html#master-urls) is in this case the local machine, with as many worker threads as logical cores on your machine.


### 3.2 Amazon Elastic MapReduce (EMR)

Following steps walk through launching GRNBoost on Amazon Elastic MapReduce. Be aware that running on AWS can incur a monetary cost. We will focus on using the Amazon web interface.



TODO
