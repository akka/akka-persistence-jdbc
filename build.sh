#!/bin/bash
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
rm ./bintray.sbt

wait 5432 PostgreSQL
wait 3306 MySQL
wait 1521 Oracle
wait 1433 SqlSever

sbt ++$TRAVIS_SCALA_VERSION test