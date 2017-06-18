#!/bin/bash
echo "==================  Help for SqlServer cli  ========================"
echo "================================================================="
docker exec -it sqlserver /opt/mssql-tools/bin/sqlcmd -S localhost -U sa -P P4ssw0rd