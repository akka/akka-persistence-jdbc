#!/bin/bash
rm ./bintray.sbt
sbt "test-only *Mysql*,*Postgres*"