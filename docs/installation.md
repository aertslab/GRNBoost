[Home](../README.md) | [__Installation Guide__](installation.md) | [User Guide](user_guide.md) | [Command Line Reference](cli_reference.md) | [Developer Guide](developer_guide.md)

# Installation Guide

This page describes how to obtain GRNBoost on different operating systems.

1. [Download pre-built artifacts](#1-download-pre-built-artifacts)
2. [Build GRNBoost from source](#2-build-grnboost-from-source)
3.  [Troubleshooting](#3-troubleshooting)

## 1 Download Pre-built Artifacts

TODO

## 2 Build GRNBoost from Source

Building GRNBoost from source requires additional software, make sure you have these installed on your system.
1. __[Git](https://git-scm.com/)__ for checking out the code bases from their Github repository.
2. __[SBT](http://www.scala-sbt.org/)__ for building __GRNBoost__ from source.
3. __[Maven](https://maven.apache.org/)__ for building the xgboost Java bindings.
4. A C++ compiler.

#### Linux (Ubuntu)

On Ubuntu or Debian, using the classic `apt-get` routine is the best option.

* __Git__:  https://www.digitalocean.com/community/tutorials/how-to-install-git-on-ubuntu-14-04

    ```bash
    sudo apt-get update
    sudo apt-get install git
    ```

* __SBT__: http://www.scala-sbt.org/1.x/docs/Installing-sbt-on-Linux.html

    ```bash
    $ echo "deb https://dl.bintray.com/sbt/debian /" | sudo tee -a /etc/apt/sources.list.d/sbt.list
    $ sudo apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 2EE0EA64E40A89B84B2DF73499E82A75642AC823
    $ sudo apt-get update
    $ sudo apt-get install sbt
    ```

* __Maven__: https://www.mkyong.com/maven/how-to-install-maven-in-ubuntu/

    ```bash
    $ sudo apt-get update
    $ sudo apt-get install maven
    ```    

#### MacOS

On Mac, we recommend the handy [Homebrew](https://brew.sh/) package manager. Other installation options are available, please refer to the software packages' documentation for more info.

* __Git__: https://git-scm.com/book/en/v1/Getting-Started-Installing-Git#Installing-on-Mac

    ```bash
    $ brew install git
    ```

* __SBT__: http://www.scala-sbt.org/0.13/docs/Installing-sbt-on-Mac.html

    ```bash
    $ brew install sbt
    ```

* __Maven__: http://brewformulas.org/Maven

    ```bash
    $ brew install maven
    ```

* __GCC__: https://gcc.gnu.org/

    ```
    $ brew install gcc --without-multilib
    ```

### 2.1 Build xgboost

GRNBoost depends upon xgboost, so we build this first. Xgboost is essentially a C++ library with Java, Scala, R and Python wrappers. We first compile the C++ core component and then build and install the Java components using Maven.

The following instructions contains excerpts from the [xgboost build guide](https://xgboost.readthedocs.io/en/latest/build.html):

1. Using git, [clone](https://git-scm.com/docs/git-clone) the xgboost Github repository (recursively! -- we need the sub-projects as well) :

    ```
    $ git clone --recursive https://github.com/dmlc/xgboost
    ```

2. Go the xgboost directory and build the xgboost C++ components:

    * __Linux (Ubuntu)__

        ```bash
        $ cd xgboost
        $ make -j4
        ```

    * __MacOS__

        ```bash
        $ cd xgboost
        $ ./build.sh
        ```

3. Go to the `xgboost/jvm-packages` directory and build the Java components using Maven.

    ```bash
    $ cd jvm-packages
    $ mvn -DskipTests install   
    ```

4. Maven installs .jar artifacts in the local Maven repository, typically found at `~/.m2/`. Check whether you can find the `xgboost4j-0.7.jar` artifact.

    ```bash
    $ ls ~/.m2/repository/ml/dmlc/xgboost4j/0.7/
    ```

    You should see something like:

    ```bash
    $ _remote.repositories	xgboost4j-0.7-jar-with-dependencies.jar	xgboost4j-0.7-sources.jar	xgboost4j-0.7.jar	xgboost4j-0.7.pom
    ```    

### 2.2 Build GRNBoost

Now we installed `xgboost4j-0.7.jar` into our local Maven repository, we can proceed to building GRNBoost.

> Note: unless breaking changes are introduced, GRNBoost should work with higher versions than 0.7 in the future.

1. Clone the GRNBoost repository:

    ```
    $ git clone https://github.com/aertslab/GRNBoost/    
    ```

2. Go to the GRNBoost directory and build it using SBT:

    ```
    $ cd GRNBoost
    $ sbt assembly
    ```

3. We're done! Find the `GRNBoost.jar` artifact in the `target` directory.

    ```
    $ ls target/scala-2.11/
    # you should see:
    $ classes   GRNBoost.jar
    ```

## 3 Troubleshooting

### 3.1 Compiling on MacOS

Compiling the xgboost C++ components can be a tricky affair (especially on MacOS). As a GRNBoost user, you will typically launch GRNBoost Spark jobs on a linux machine. If you are a developer and want to be able to test things on a Mac, for example launch unit tests using xgboost from an IDE, you may want (or need) to suffer through compiling xgboost on MacOS. Following are some notes that helped troubleshoot compilation on MacOS.

* Problems installing **XGBoost** with multithreaded support on Mac OS:
    * **issue 1**: making xgboost requires gcc-6
        * https://www.ibm.com/developerworks/community/blogs/jfp/entry/Installing_XGBoost_on_Mac_OSX?lang=en
        * uncomment and fix the 2 lines in the `make/config.mk` file as described in the blog post above
    * **issue 2**: the default compiler is clang, which doesn't have openmp support needed for xgboost
        * remedy: `brew install gcc --without-multilib`
    * **issue 3**: the above doesn't work, error message:

        ```
        Last 15 lines from /Users/tmo/Library/Logs/Homebrew/gcc/01.configure:
        checking for gawk... no
        checking for mawk... no
        checking for nawk... no
        checking for awk... awk
        checking for libatomic support... yes
        checking for libcilkrts support... yes
        checking for libitm support... yes
        checking for libsanitizer support... yes
        checking for libvtv support... no
        checking for libmpx support... no
        checking for gcc... clang
        checking for C compiler default output file name...
        configure: error: in `/private/tmp/gcc-20170217-49092-1n2yfeq/gcc-6.3.0/build':
        configure: error: C compiler cannot create executables
        See `config.log' for more details.
        READ THIS: http://docs.brew.sh/Troubleshooting.html
        These open issues may also help:
        gcc: unable to complete the make process https://github.com/Homebrew/homebrew-core/issues/4883
        `brew upgrade gcc` takes over 10 hours on 2014 MBP, core i5 https://github.com/Homebrew/homebrew-core/issues/8796
        ```

        * remedy: http://apple.stackexchange.com/questions/216573/cant-compile-source-code-on-mac

        > Start Xcode, select 'Preferences', then 'Locations'. You'll notice a dropdown control at 'Command Line Tools'. Select the newest version, close the dialog window, then call brew again.
    * **issue 4**: installation can take a LONG time!        
    * brew complains about manually (un)linking stuff: do as instructed    

* check gcc installations:

    ```
    dhcp-10-33-234-169:xgboost tmo$ gcc --version
    Configured with: --prefix=/Applications/Xcode.app/Contents/Developer/usr --with-gxx-include-dir=/usr/include/c++/4.2.1
    Apple LLVM version 8.0.0 (clang-800.0.42.1)
    Target: x86_64-apple-darwin15.6.0
    Thread model: posix
    InstalledDir: /Applications/Xcode.app/Contents/Developer/Toolchains/XcodeDefault.xctoolchain/usr/bin

    dhcp-10-33-234-169:xgboost tmo$ gcc-6 --version
    gcc-6 (Homebrew GCC 6.3.0_1 --without-multilib) 6.3.0
    Copyright (C) 2016 Free Software Foundation, Inc.
    This is free software; see the source for copying conditions.  There is NO
    warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

    dhcp-10-33-234-169:xgboost tmo$ g++-6 --version
    g++-6 (Homebrew GCC 6.3.0_1 --without-multilib) 6.3.0
    Copyright (C) 2016 Free Software Foundation, Inc.
    This is free software; see the source for copying conditions.  There is NO
    warranty; not even for MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
    ```

* solution:
    * `config.mk`

    ```
    # choice of compiler, by default use system preference.
    # !!! uncommented and adapted following 2 lines !!!
    # See: https://www.ibm.com/developerworks/community/blogs/jfp/entry/Installing_XGBoost_on_Mac_OSX?lang=en
    export CC = gcc-6
    export CXX = g++-6
    # export MPICXX = mpicxx
    # export LDFLAGS = -pthread -lm -mmacosx-version-min=10.9

    # the additional link flags you want to add
    ADD_LDFLAGS =
    ```

    * then clean and make again:

    ```
    make clean_all
    make -j4
    cd jvm-packages/
    mvn clean
    mvn -DskipTests intall    
    ```
