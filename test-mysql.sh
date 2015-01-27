#!/bin/bash
source include.sh

start mysql
wait 3306 MySQL
sbt "test-only *Mysql*"
stop mysql