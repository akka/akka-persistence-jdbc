#!/bin/bash
fig --file fig-postgres.yml up -d --allow-insecure-ssl

while true; do
  if ! nc -z boot2docker 5432
  then
    echo "Postgresql not running yet, retrying..."
    sleep 1
  else
    echo "Postgresql is running"
    break;
  fi
done;

sbt "test-only *Postgres*"

fig stop
fig rm --force
