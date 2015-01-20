#!/bin/bash
fig --file fig-h2.yml up -d --allow-insecure-ssl

while true; do
  if ! nc -z boot2docker 1522
  then
    echo "H2 not running yet, retrying..."
    sleep 1
  else
    echo "H2 is running"
    break;
  fi
done;

sbt "test-only *H2*"

fig stop
fig rm --force
