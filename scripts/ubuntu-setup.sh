#!/bin/bash
# Script for ubuntu-14.0.4 scala toolchain installation
# Run: sudo ubuntu-setup.sh
# Warning: it's interactive, you have to answer some yessirs and hit some
#  enters.

# repositories
add-apt-repository -y ppa:webupd8team/java
echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
apt-get -y update

# java8
export DEBIAN_FRONTEND=noninteractive
echo oracle-java8-installer shared/accepted-oracle-license-v1-1 select true | debconf-set-selections
apt-get -y install oracle-java8-installer
# apt-get install oracle-java8-set-default

# sbt
apt-get install sbt
