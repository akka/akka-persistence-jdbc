#!/bin/bash
#
# Copyright 2016 Dennis Vriend
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
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

docker-compose -f scripts/docker-compose.yml kill postgres
docker-compose -f scripts/docker-compose.yml rm -f postgres
docker-compose -f scripts/docker-compose.yml up -d postgres
wait 5432 Postgres
