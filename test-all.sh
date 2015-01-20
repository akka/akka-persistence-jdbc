#!/bin/bash
fig --file fig.yml up -d --allow-insecure-ssl

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

sbt test

fig stop
fig rm --force
