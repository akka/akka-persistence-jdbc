#!/bin/bash
fig --file fig-mysql.yml up -d --allow-insecure-ssl

while true; do
  if ! nc -z boot2docker 3306
  then
    echo "Mysql not running yet, retrying..."
    sleep 1
  else
    echo "MySQL is running"
    break;
  fi
done;

sbt "test-only *Mysql*"

fig stop
fig rm --force