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
#!/bin/bash
echo "==================     Help for psql    ========================="
echo "\l or \list                : shows all databases"
echo "\d                         : shows all tables, views and sequences"
echo "\d table_name              : describe table, view, sequence, or index"
echo "\c database_name           : connect to a database"
echo "\q                         : quit"
echo "\?                         : for more commands"
echo "====================    Extensions    ==========================="
echo "create extension pgcrypto; : installs cryptographic functions"
echo "====================    Some SQL    ============================="
echo "select gen_random_uuid();  : returns a random uuid (pgcrypto)"
echo "select version();          : return the server version"
echo "select current_date;       : returns the current date"
echo "================================================================="
docker exec -it postgres psql --dbname=docker --username=docker