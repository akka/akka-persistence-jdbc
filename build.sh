#!/bin/bash
rm ./bintray.sbt
source ./include.sh
start oracle
start h2
wait 1521 Oracle
wait 1522 H2
wait 3306 Mysql
wait 5432 Postgres
sbt test