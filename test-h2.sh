#!/bin/bash
source include.sh

start h2
wait 1522 H2
sbt "test-only *H2*"
stop h2