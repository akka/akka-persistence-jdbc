#!/bin/bash
rm ./bintray.sbt
source ./include.sh
start h2oracle
wait 1521 Oracle
wait 1522 H2
sbt test