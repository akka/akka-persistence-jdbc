#!/bin/bash
echo "==================  Help for SqlServer cli  ========================"
echo "================================================================="

docker exec -it sqlserver-test /opt/mssql-tools18/bin/sqlcmd -N o -S localhost -U sa -P docker123abc# -d docker
