#!/bin/bash
source include.sh

start oracle
wait 1521 Oracle-XE
sbt "test-only *Oracle*"
stop oracle
