#!/bin/bash
source include.sh

start postgres
wait 5432 Postgres
sbt clean "test-only *Postgres*"
stop postgres
