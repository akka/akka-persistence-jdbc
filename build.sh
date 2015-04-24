#!/bin/bash
rm ./bintray.sbt
sbt "test-only *Mysql*"
sbt "test-only *Postgres*"