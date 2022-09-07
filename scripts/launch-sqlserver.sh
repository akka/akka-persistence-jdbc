#!/bin/bash
#
# Copyright 2016 Dennis Vriend
# Copyright (C) 2019 - 2022 Lightbend Inc. <https://www.lightbend.com>
#
export VM_HOST="${VM_HOST:-localhost}"

# Wait for a certain service to become available
# Usage: wait 1433 SqlServer
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

docker-compose -f scripts/docker-compose.yml kill sqlserver
docker-compose -f scripts/docker-compose.yml rm -f sqlserver
docker-compose -f scripts/docker-compose.yml up -d sqlserver
wait 1433 SqlServer
