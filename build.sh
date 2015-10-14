#!/bin/bash
rm ./bintray.sbt
source ./include.sh
start h2oracle
wait 1521 Oracle
wait 1522 H2
#wait 3306 Mysql
wait 5432 Postgres
sbt "test-only *Postgres*"