#!/usr/bin/env bash
# I have added an alias for the VM IP address 'boot2docker' in /etc/hosts
export VM_HOST=boot2docker

start() {
  compose_file=compose/docker-compose-${1:-all}.yml
  stop $1 && docker-compose --file $compose_file up -d
}

stop() {
  compose_file=compose/docker-compose-${1:-all}.yml
  docker-compose --file $compose_file stop && docker-compose --file $compose_file rm --force
}

# Wait for a certain service to become available
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
