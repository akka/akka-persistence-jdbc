#!/bin/bash
source include.sh

start
wait 3306 Mysql
wait 1521 Oracle
wait 5432 Postgres
wait 1522 H2
sbt test
stop