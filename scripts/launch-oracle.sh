#!/bin/bash
#
# Copyright 2016 Dennis Vriend
# Copyright (C) 2019 - 2021 Lightbend Inc. <https://www.lightbend.com>
#
export VM_HOST="${VM_HOST:-localhost}"

# Wait for a certain service to become available
# Usage: wait 3306 Mysql
wait() {
while true; do
  if ! nc -z $VM_HOST $1
  then
    echo "$2 not available, retrying..."
    sleep 1
  else
    echo "$2 is available"
    break;
  fi
done;
}

docker-compose -f scripts/docker-compose.yml kill oracle
docker-compose -f scripts/docker-compose.yml rm -f oracle
docker-compose -f scripts/docker-compose.yml up -d oracle
wait 1521 Oracle
