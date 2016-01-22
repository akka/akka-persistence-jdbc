#!/bin/bash
export VM_HOST="${VM_HOST:-boot2docker}"

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

docker rm -f $(docker ps -aq)
docker-compose --x-networking up -d
wait 3306 MySQL
wait 5432 Postgres
wait 1521 Oracle
sbt clean test
docker rm -f $(docker ps -aq)