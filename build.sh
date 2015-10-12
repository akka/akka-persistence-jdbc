#!/bin/bash
rm ./bintray.sbt
source ./include.sh
wait 1521 Oracle
wait 1522 H2
sbt test