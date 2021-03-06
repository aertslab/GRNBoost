# GRNBoost

[![Build Status](https://travis-ci.org/aertslab/GRNBoost.svg?branch=master)](https://travis-ci.org/aertslab/GRNBoost)

## Introduction

GRNBoost is a library built on top of [Apache Spark](http://spark.apache.org/) that implements a scalable
strategy for gene regulatory network (GRN) inference.

Inferring a gene regulatory network (GRN) from gene expression data is a computationally expensive task, exacerbated by increasing data sizes due to advances in high-throughput gene profiling technology.

GRNBoost was inspired by [GENIE3](http://www.montefiore.ulg.ac.be/~huynh-thu/GENIE3.html), a popular algorithm for GRN inference. GENIE3 breaks up the inference problem into a number of [tree-based](https://en.wikipedia.org/wiki/Decision_tree) [ensemble](https://en.wikipedia.org/wiki/Ensemble_learning) ([Random Forest](https://en.wikipedia.org/wiki/Random_forest)) [nonlinear regressions](https://en.wikipedia.org/wiki/Nonlinear_regression), building a predictive model for the expression profile of each gene in the dataset in function of the expression profiles of a collection of candidate regulatory genes ([transcription factors](https://en.wikipedia.org/wiki/Transcription_factor)). The regression models act as a feature selection mechanism, they yield the most predictive regulators for the target genes as candidate links in the resulting gene regulatory network.

GRNBoost adopts GENIE3's algorithmic blueprint and aims at improving its runtime performance and data size capability. GRNBoost does this by reframing the GENIE3 _multiple regression_ approach into an Apache Spark MapReduce-style pipeline, and by replacing the regression algorithm by the current state-of-the-art among tree-based machine learning algorithms, a [Gradient Boosting](https://en.wikipedia.org/wiki/Gradient_boosting) variant called [xgboost](https://xgboost.readthedocs.io/en/latest/).

## Getting Started

* [Installation guide](docs/installation.md)
* [User guide](docs/user_guide.md)
* [Command line reference guide](docs/cli_reference.md)
* [Developer Guide](docs/developer_guide.md)
* [Report an issue](https://github.com/aertslab/GRNBoost/issues/new)

## License

GRNBoost is available via the [3-Clause BSD license](https://opensource.org/licenses/BSD-3-Clause).

## References

GRNBoost was developed at the [Laboratory of Computational Biology](https://gbiomed.kuleuven.be/english/research/50000622/lcb) ([Stein Aerts](http://www.vib.be/en/research/scientists/Pages/Stein-Aerts-Lab.aspx)) as an optional component for the [SCENIC](https://gbiomed.kuleuven.be/english/research/50000622/lcb/tools/scenic) workflow.

Sara Aibar,	Carmen Bravo González-Blas,	Thomas Moerman,	Vân Anh Huynh-Thu,	Hana Imrichova,	Gert Hulselmans,	
Florian Rambow,	Jean-Christophe Marine,	Pierre Geurts,	Jan Aerts,	Joost van den Oord,	Zeynep Kalender Atak,	
Jasper Wouters & Stein Aerts [SCENIC: single-cell regulatory network inference and clustering](http://dx.doi.org/10.1038/nmeth.4463). Nature Methods (2017) doi:10.1038/nmeth.4463
