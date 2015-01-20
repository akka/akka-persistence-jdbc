#!/bin/bash
fig --file fig-oracle.yml up -d --allow-insecure-ssl

while true; do
  if ! nc -z boot2docker 1521
  then
    echo "Oracle not running yet, retrying..."
    sleep 1
  else
    echo "Oracle is running"
    break;
  fi
done;

sbt "test-only *Oracle*"

fig stop
fig rm --force