# GRNBoost

A scalable framework for gene regulatory network inference using Apache Spark and XGBoost. GRNBoost was inspired by [GENIE3](http://www.montefiore.ulg.ac.be/~huynh-thu/GENIE3.html).

## Building from source
* This project depends on XGBoost, see the [installation instructions](https://xgboost.readthedocs.io/en/latest/build.html).
    * **Ubuntu**
    
        ```
        git clone --recursive https://github.com/dmlc/xgboost
        cd xgboost
        make -j4
        cd jvm-packages
        mvn -DskipTests install        
        ```            
        
    * **MacOS**
    
        ```
        git clone --recursive https://github.com/dmlc/xgboost
        cd xgboost
        ./build.sh
        cd jvm-packages
        mvn -DskipTests install
        ```

* Use [SBT](http://www.scala-sbt.org/) to build the project

    ```
    $ sbt
    > compile 
    > test
    > package
    ```

## Submitting Spark jobs
* TODO
