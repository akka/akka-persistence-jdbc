package akka.persistence.h2.common

import akka.persistence.jdbc.common.JdbcConnection

object H2Connection extends H2Config with JdbcConnection
