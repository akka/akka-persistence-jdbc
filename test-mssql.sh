#!/bin/bash

source include.sh

# there is no usable MS SQL Server docker image publically available at the moment, but there is a Vagrant box:
#   https://github.com/fgrehm/vagrant-mssql-express
# If installed and configured with default parameters then the following should work for you:

sbt -Dmssql.host=192.168.50.4 -Djdbc-connection.username=sa -Djdbc-connection.password='#SAPassword!' "test-only *Mssql*"
