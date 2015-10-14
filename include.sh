#!/usr/bin/env bash
export VM_HOST="${VM_HOST:-boot2docker}"

# Start all services; uses the associated docker-compose file
# Usage (start all services): start
# Usage (start only mysql): start mysql
start() {
  compose_file=docker-compose-${1:-all}.yml
  stop $1 && docker-compose --file $compose_file up -d
}

# stop and remove all services; uses the associated docker-compose file
# Usage (stop all services): stop
# Usage (stop only mysql): stop mysql
stop() {
  compose_file=docker-compose-${1:-all}.yml
  docker-compose --file $compose_file stop && docker-compose --file $compose_file rm --force
}

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
