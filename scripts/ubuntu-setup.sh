#!/bin/bash
# Script for ubuntu-14.0.4 scala toolchain installation
# Run: sudo ubuntu-setup.sh
# Warning: it's interactive, you have to answer some yessirs and hit some
#  enters.

# git
apt-get remove git
add-apt-repository ppa:git-core/ppa
apt-get update
apt-get install git

# java8
add-apt-repository ppa:webupd8team/java
apt-get update
apt-get install oracle-java8-installer
# apt-get install oracle-java8-set-default

# scala
wget http://www.scala-lang.org/files/archive/scala-2.11.8.deb
dpkg -i scala-2.11.8.deb

# sbt
echo "deb https://dl.bintray.com/sbt/debian /" | tee -a /etc/apt/sources.list.d/sbt.list
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv 642AC823
apt-get update
apt-get install sbt
