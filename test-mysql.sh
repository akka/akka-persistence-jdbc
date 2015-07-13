#!/bin/bash
source include.sh

start mysql
wait 3306 MySQL
sbt clean "test-only *Mysql*"
stop mysql