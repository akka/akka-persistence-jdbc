#!/bin/bash
source include.sh

start oracle
wait 1521 Oracle-XE
sbt clean "test-only *Oracle*"
stop oracle
