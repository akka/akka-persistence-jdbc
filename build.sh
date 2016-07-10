#!/bin/bash
rm ./bintray.sbt
sbt "test-only *Postgres* *MySQL* *H2*"