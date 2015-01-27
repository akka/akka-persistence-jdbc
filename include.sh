# Stops, removes all services and creates new ones, fig likes to reuse ones, even when they are rebuild
start() {
  fig_file=fig-${1:-all}.yml
  stop $1 && fig --file $fig_file up -d --allow-insecure-ssl
}

# Stop and removes all services defined by fig
stop() {
  fig_file=fig-${1:-all}.yml
  fig --file $fig_file stop && fig --file $fig_file rm --force
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
