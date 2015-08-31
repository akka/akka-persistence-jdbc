#!/usr/bin/env bash
# Stops, removes all services and creates new ones, fig likes to reuse ones, even when they are rebuild

evalMachine() {
  eval "$(docker-machine env dev)"
}

start() {
  evalMachine
  compose_file=compose/docker-compose-${1:-all}.yml
  stop $1 && docker-compose --file $compose_file up -d --allow-insecure-ssl
}

# Stop and removes all services defined by fig
stop() {
  eval "$(docker-machine env dev)"
  compose_file=compose/docker-compose-${1:-all}.yml
  docker-compose --file $compose_file stop && docker-compose --file $compose_file rm --force
}

# Wait for a certain service to become available
wait() {
while true; do
  if ! nc -z boot2docker $1
  then
    echo "$2 not available, retrying..."
    sleep 1
  else
    echo "$2 is available"
    break;
  fi
done;
}
