#!/usr/bin/env bash

# This script describes the steps to execute in order to build xgboost on an Amazon linux node.

# NOTE
# This script was tested on an Amazon EMR instance, on date: 21 Aug 2017.
# It builds version 0.7

# INSTALL SOFTWARE
# ----------------

# * git
sudo yum install git

# * cmake
wget https://cmake.org/files/v3.9/cmake-3.9.1-Linux-x86_64.sh
chmod +x cmake-3.9.1-Linux-x86_64.sh
./cmake-3.9.1-Linux-x86_64.sh
export PATH=$PATH:/home/hadoop/cmake-3.9.1-Linux-x86_64/bin/

# * maven
sudo wget http://repos.fedorapeople.org/repos/dchen/apache-maven/epel-apache-maven.repo -O /etc/yum.repos.d/epel-apache-maven.repo
sudo sed -i s/\$releasever/6/g /etc/yum.repos.d/epel-apache-maven.repo
sudo yum install -y apache-maven

# BUILD XGBOOST
# -------------

# * clone the github repository
git clone --recursive https://github.com/dmlc/xgboost

# * build the C++ code
cd xgboost
make -j4

# * create the .jar package
cd jvm-packages
mvn -DskipTests install